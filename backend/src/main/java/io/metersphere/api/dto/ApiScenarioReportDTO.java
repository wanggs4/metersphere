package io.metersphere.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class ApiScenarioReportDTO {

    private List<StepTreeDTO> steps;

    private String console;
    private long totalTime;
    private long total;
    private long error;

    private long scenarioTotal;
    private long scenarioError;
    private long scenarioSuccess;

    private long scenarioStepTotal;
    private long scenarioStepError;
    private long scenarioStepSuccess;

    private long totalAssertions = 0;
    private long passAssertions = 0;

}
