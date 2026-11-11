package com.yash.handover.adapters.ci;

import java.util.List;

/** Read-only access to CI/CD pipeline definitions. */
public interface CISource {

    List<PipelineDefinition> pipelines();
}
