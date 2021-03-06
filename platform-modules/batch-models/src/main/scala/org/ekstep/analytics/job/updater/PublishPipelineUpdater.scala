package org.ekstep.analytics.job.updater

import org.apache.spark.SparkContext
import org.ekstep.analytics.framework.JobDriver
import org.ekstep.analytics.updater.UpdateTextbookSnapshotDB
import org.ekstep.analytics.framework.IJob
import org.ekstep.analytics.updater.UpdatePublishPipelineSummary

object PublishPipelineUpdater extends optional.Application with IJob {

	def main(config: String)(implicit sc: Option[SparkContext] = None) {
		implicit val sparkContext: SparkContext = sc.getOrElse(null);
		JobDriver.run("batch", config, UpdatePublishPipelineSummary);
	}

}