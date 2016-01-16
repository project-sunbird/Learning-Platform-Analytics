#!/usr/bin/env bash

export SPARK_HOME=/home/ec2-user/spark-1.5.2-bin-hadoop2.3
export JAVA_HOME=/usr/java/current

## Job to run daily
cd /mnt/data/analytics/scripts
endDate=$(date --date yesterday "+%Y-%m-%d")

aser_config='{"search":{"type":"s3","queries":[{"prefix":"prod.telemetry.unique-","endDate":"'$endDate'","delta":0}]},"filters":[{"name":"eventId","operator":"IN","value":["OE_ASSESS","OE_START","OE_END","OE_LEVEL_SET","OE_INTERACT","OE_INTERRUPT"]}],"model":"org.ekstep.analytics.model.GenericSessionSummaryV2","modelParams":{"modelVersion":"1.0","modelId":"GenericSessionSummarizer"},"output":[{"to":"console","params":{"printEvent": false}},{"to":"kafka","params":{"brokerList":"10.10.1.171:9092","topic":"prod.analytics.screener"}}],"parallelization":8,"appName":"Generic Session Summarizer","deviceMapping":true}'

nohup $SPARK_HOME/bin/spark-submit --master local[*] --jars /mnt/data/analytics/models/analytics-framework-0.5.jar --class org.ekstep.analytics.job.GenericSessionSummarizerV2 /mnt/data/analytics/models/batch-models-1.0.jar --config "$aser_config" > "logs/$endDate-sess-summary.log" 2>&1&