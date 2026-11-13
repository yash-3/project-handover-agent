package com.yash.handover.knowledge;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;

/**
 * Postgres + pgvector store. Vector search uses cosine distance; keyword search uses Postgres
 * full-text search; the two rankings are fused with reciprocal-rank fusion. Everything is scoped by
 * project, so projects never see each other's chunks. All SQL is parameterised.
 */
public final class PgVectorChunkStore implements ChunkStore {

    private final DataSource ds;
    private final int dims;

    public PgVectorChunkStore(DataSource ds, int dims) {
        this.ds = ds;
        this.dims = dims;
        initSchema();
    }

    public static DataSource dataSource(String url, String user, String password) {
        PGSimpleDataSource d = new PGSimpleDataSource();
        d.setUrl(url);
        d.setUser(user);
        d.setPassword(password);
        return d;
    }

    private void initSchema() {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE EXTENSION IF NOT EXISTS vector");
            s.execute("CREATE TABLE IF NOT EXISTS knowledge_chunk ("
                    + "id BIGSERIAL PRIMARY KEY, project TEXT NOT NULL, repo_id TEXT NOT NULL, path TEXT NOT NULL,"
                    + "start_line INT NOT NULL, end_line INT NOT NULL, language TEXT NOT NULL, content TEXT NOT NULL,"
                    + "embedding vector(" + dims + ") NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS knowledge_chunk_project ON knowledge_chunk(project, repo_id)");
            s.execute("CREATE INDEX IF NOT EXISTS knowledge_chunk_fts ON knowledge_chunk "
                    + "USING GIN (to_tsvector('simple', content))");
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot initialise the knowledge schema: " + e.getMessage(), e);
        }
    }

    @Override
    public void replace(String project, String repoId, List<Chunk> chunks, List<float[]> vectors) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement del = c.prepareStatement(
                    "DELETE FROM knowledge_chunk WHERE project = ? AND repo_id = ?");
                    PreparedStatement ins = c.prepareStatement("INSERT INTO knowledge_chunk "
                            + "(project, repo_id, path, start_line, end_line, language, content, embedding) "
                            + "VALUES (?,?,?,?,?,?,?,?::vector)")) {
                del.setString(1, project);
                del.setString(2, repoId);
                del.executeUpdate();
                for (int i = 0; i < chunks.size(); i++) {
                    Chunk ch = chunks.get(i);
                    ins.setString(1, project);
                    ins.setString(2, repoId);
                    ins.setString(3, ch.path());
                    ins.setInt(4, ch.startLine());
                    ins.setInt(5, ch.endLine());
                    ins.setString(6, ch.language());
                    ins.setString(7, ch.text());
                    ins.setString(8, literal(vectors.get(i)));
                    ins.addBatch();
                }
                ins.executeBatch();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot store chunks: " + e.getMessage(), e);
        }
    }

    @Override
    public List<SearchHit> search(String project, float[] queryVector, String queryText, int topK) {
        int pool = Math.max(topK * 4, 20);
        Map<Long, Chunk> chunks = new LinkedHashMap<>();
        Map<Long, Double> score = new LinkedHashMap<>();
        try (Connection c = ds.getConnection()) {
            query(c, "SELECT id, repo_id, path, start_line, end_line, language, content FROM knowledge_chunk "
                    + "WHERE project = ? ORDER BY embedding <=> ?::vector LIMIT ?",
                    project, literal(queryVector), pool, chunks, score);
            query(c, "SELECT id, repo_id, path, start_line, end_line, language, content FROM knowledge_chunk "
                    + "WHERE project = ? AND to_tsvector('simple', content) @@ plainto_tsquery('simple', ?) "
                    + "ORDER BY ts_rank(to_tsvector('simple', content), plainto_tsquery('simple', ?)) DESC LIMIT ?",
                    project, queryText, pool, chunks, score);
        } catch (SQLException e) {
            throw new IllegalStateException("Search failed: " + e.getMessage(), e);
        }
        return score.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(en -> new SearchHit(chunks.get(en.getKey()), en.getValue()))
                .toList();
    }

    private void query(Connection c, String sql, String project, String param, int limit,
            Map<Long, Chunk> chunks, Map<Long, Double> score) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, project);
            ps.setString(2, param);
            int next = 3;
            if (sql.contains("ts_rank")) {
                ps.setString(next++, param);
            }
            ps.setInt(next, limit);
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    long id = rs.getLong("id");
                    chunks.putIfAbsent(id, new Chunk(rs.getString("repo_id"), rs.getString("path"),
                            rs.getInt("start_line"), rs.getInt("end_line"), rs.getString("language"),
                            rs.getString("content")));
                    score.merge(id, ChunkStore.rrf(rank++), Double::sum);
                }
            }
        }
    }

    private static String literal(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
