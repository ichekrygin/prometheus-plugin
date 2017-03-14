package org.jenkinsci.plugins.prometheus;

import hudson.model.Job;
import hudson.model.Run;
import hudson.util.RunList;
import io.prometheus.client.Collector;
import io.prometheus.client.Counter;
import io.prometheus.client.Summary;
import org.jenkinsci.plugins.prometheus.util.Callback;
import org.jenkinsci.plugins.prometheus.util.Jobs;
import org.jenkinsci.plugins.prometheus.util.Runs;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;


public class JobCollector extends Collector {
    private static final Logger logger = Logger.getLogger(JobCollector.class.getName());

    private Summary summary;
    private Counter counter;

    public JobCollector() {}

    @Override
    public List<MetricFamilySamples> collect() {
        final List<MetricFamilySamples> samples = new ArrayList<MetricFamilySamples>();
        final List<Job> processedJobs = new ArrayList<Job>();
        final String fullname = "builds";
        final String subsystem = "jenkins";
        String[] summaryLabelNameArray = {};
        String[] jobsLabelNameArray = {"jenkins_job", "status"};

        summary = Summary.build()
                .name(fullname + "_duration")
                .subsystem(subsystem)
                .labelNames(summaryLabelNameArray)
                .quantile(0.5, 0.05)
                .quantile(0.75, 0.025)
                .quantile(0.9, 0.01)
                .quantile(0.99, 0.001)
                .quantile(0.999, 0.0001)
                .help("Jenkins build times in milliseconds")
                .create();

        counter = Counter.build()
                .name("jenkins_builds_counters").help("Total builds.")
                .labelNames("jenkins_job").create();

        Jobs.forEachJob(new Callback<Job>() {
            @Override
            public void invoke(Job job) {
                for (Job old : processedJobs) {
                    if (old.getFullName().equals(job.getFullName())) {
                        // already added
                        return;
                    }
                }
                processedJobs.add(job);
                appendJobMetrics(job);
            }
        });
        if (summary.collect().get(0).samples.size() > 0) {
            samples.addAll(summary.collect());
            samples.addAll(counter.collect());
        }
        return samples;
    }

    protected void appendJobMetrics(Job job) {
        logger.info("Job: " + job.getFullName());
        String[] summaryLabelValueArray = {};
        String[] jobsLabelValueArray = {job.getFullName()};
        RunList<Run> builds = job.getBuilds();
        if (builds != null) {
            for (Run build : builds) {
                if (Runs.includeBuildInMetrics(build)) {
                    long buildDuration = build.getDuration();
                    summary.labels(summaryLabelValueArray).observe(buildDuration);
                    //jobsLabelValueArray[1] = build.getResult().toString();
                    counter.labels(jobsLabelValueArray).inc();
                }
            }
        }
    }
}