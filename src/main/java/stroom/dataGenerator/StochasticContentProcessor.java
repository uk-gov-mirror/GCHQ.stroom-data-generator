package stroom.dataGenerator;

import stroom.dataGenerator.StochasticTemplateProcessor;
import stroom.dataGenerator.TemplateProcessingException;
import stroom.dataGenerator.TemplateProcessorFactory;
import stroom.dataGenerator.config.EventGenConfig;
import stroom.dataGenerator.config.StochasticTemplateConfig;
import stroom.dataGenerator.config.TemplateConfig;

import java.io.FileNotFoundException;
import java.io.Writer;
import java.time.Instant;
import java.util.*;

public class StochasticContentProcessor {
    private List <StochasticTemplateProcessor> contentProcessors;
    private TemplateProcessor betweenEventProcessor;
    private final String streamName;
    public StochasticContentProcessor(EventGenConfig appConfig, List<StochasticTemplateConfig> contentConfig, TemplateConfig betweenEventConfig,
                                      String streamName) throws FileNotFoundException {
        this.streamName = streamName;
        TemplateProcessorFactory templateProcessorFactory = new TemplateProcessorFactory(appConfig);
        contentProcessors = new ArrayList<>();
        for (StochasticTemplateConfig config : contentConfig){
            contentProcessors.add ( new StochasticTemplateProcessor(templateProcessorFactory, streamName, config));
        }
        if (betweenEventConfig != null){
            betweenEventProcessor = templateProcessorFactory.createProcessor(betweenEventConfig, streamName);
        }
    }

    public ProcessingContext process (ProcessingContext initialContext, Instant endTime, Writer output) {
        Random random = new Random();
        ProcessingContext context = initialContext;
        Instant currentTime = context.getTimestamp();
        boolean firstEvent = true;
        while (currentTime.isBefore(endTime)){
            Map<Long, StochasticTemplateProcessor> nextEventTimes = new HashMap<>();
            for (StochasticTemplateProcessor processor : contentProcessors){
                nextEventTimes.put(processor.nextEventAfterMs(random.nextDouble()), processor);
            }
            Long shortestInterval = nextEventTimes.keySet().iterator().next();
            for (Long delay : nextEventTimes.keySet()){
                if (delay < shortestInterval){
                    shortestInterval = delay;
                }
            }

            StochasticTemplateProcessor processor = nextEventTimes.get(shortestInterval);
            currentTime = Instant.ofEpochMilli(currentTime.toEpochMilli() + shortestInterval);
            context = new ProcessingContext(initialContext, currentTime);
            try {
                if (!firstEvent){
                    betweenEventProcessor.process(context, output);
                }
                firstEvent = false;
                processor.process(context, output);
            } catch (TemplateProcessingException ex){
                System.err.println("Processing error encountered in " + streamName + " : " + ex.getMessage());
                ex.printStackTrace();
                System.err.println("Not using this template for further processing");
                contentProcessors.remove(processor);
            }
        }
        return context;
    }
}
