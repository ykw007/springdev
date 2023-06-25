package com.example.demo.batch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.batch.item.ItemProcessor;

public class SampleDataItemProcessor implements ItemProcessor<SampleData, SampleData> {

    private static final Logger log = LoggerFactory.getLogger(SampleDataItemProcessor.class);

    @Override
    public SampleData process(final SampleData sd) throws Exception {
        final String c1 = sd.getC1();
        final String c2 = sd.getC2();
        final String c3 = sd.getC3();
        final String c4 = sd.getC4();
        final String c5 = sd.getC5();
        final String c6 = sd.getC6();

        final SampleData transformedSampleData = new SampleData(c1, c2, c3, c4, c5, c6);

        log.info("Converting (" + sd + ") into (" + transformedSampleData + ")");

        return transformedSampleData;
    }

}
