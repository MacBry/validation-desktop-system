package com.mac.bry.desktop.dto;

import java.util.Map;

public record ChartSeries(String name, Map<String, Number> dataPoints) {}
