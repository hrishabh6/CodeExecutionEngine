package com.hrishabh.codeexecutionengine.service.filehandlingservice;

import com.hrishabh.codeexecutionengine.dto.CodeSubmissionDTO;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The core interface for generating source code files from a submission DTO.
 * This is language-agnostic.
 */
public interface FileGenerator {
    /**
     * Generates and writes the necessary source code files for a given submission.
     *
     * @param submissionDto The DTO containing the user's code and metadata.
     * @param rootPath      The root directory where files should be created.
     * @throws IOException If a file I/O error occurs.
     */
    void generateFiles(CodeSubmissionDTO submissionDto, Path rootPath) throws IOException;
}
