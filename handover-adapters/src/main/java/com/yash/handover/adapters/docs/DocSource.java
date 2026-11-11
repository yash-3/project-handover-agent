package com.yash.handover.adapters.docs;

import java.util.List;

/** Read-only access to documentation (a docs folder, a wiki space, ...). */
public interface DocSource {

    List<Document> listDocuments();
}
