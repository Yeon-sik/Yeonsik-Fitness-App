package com.yeonsik.fitnessapp.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Application service separating the settings JSON transfer flow from local backups. */
public final class WorkoutTransferService {
    private final FitnessRepository repository;

    public WorkoutTransferService(FitnessRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("운동 저장소가 없습니다.");
        }
        this.repository = repository;
    }

    public String exportJson() {
        return WorkoutTransferCodec.encode(repository.exportWorkoutTransferDocument());
    }

    public void writeJson(OutputStream output) throws IOException {
        if (output == null) {
            throw new IOException("운동 전송 파일을 저장할 수 없습니다.");
        }
        OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        writer.write(exportJson());
        writer.flush();
    }

    public FitnessRepository.WorkoutTransferImportResult importJson(String json) {
        return repository.importWorkoutTransferDocument(WorkoutTransferCodec.decode(json));
    }

    public FitnessRepository.WorkoutTransferImportResult importJson(InputStream input)
            throws IOException {
        if (input == null) {
            throw new IOException("운동 전송 파일을 읽을 수 없습니다.");
        }
        return importJson(readUtf8(input));
    }

    private static String readUtf8(InputStream input) throws IOException {
        StringWriter output = new StringWriter();
        char[] buffer = new char[8192];
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            int length;
            while ((length = reader.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
        }
        return output.toString();
    }
}
