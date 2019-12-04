package org.ekstep.analytics.util

import org.apache.spark.sql.{Encoders, SparkSession}
import org.apache.spark.sql.functions._
import org.ekstep.analytics.job.report.StateAdminGeoReportJob.loadData
import org.ekstep.analytics.job.report.{RootOrgData, SubOrgRow}
import org.sunbird.cloud.storage.conf.AppConf

class StateAdminReportHelper(sparkSession: SparkSession) {
  implicit val this.sparkSession = sparkSession

  val tempDir = AppConf.getConfig("admin.metrics.temp.dir")
  val summaryDir = s"$tempDir/summary"

  def generateGeoBlockData()(implicit sparkSession: SparkSession) = {
    import sparkSession.implicits._
    val sunbirdKeyspace = AppConf.getConfig("course.metrics.cassandra.sunbirdKeyspace")

    val locationDF = loadData(sparkSession, Map("table" -> "location", "keyspace" -> sunbirdKeyspace), None).select(
      col("id").as("locid"),
      col("code").as("loccode"),
      col("name").as("locname"),
      col("parentid").as("locparentid"),
      col("type").as("loctype"))

    val organisationDF = loadData(sparkSession, Map("table" -> "organisation", "keyspace" -> sunbirdKeyspace), None).select(
      col("id").as("id"),
      col("isrootorg").as("isrootorg"),
      col("rootorgid").as("rootorgid"),
      col("channel").as("channel"),
      col("status").as("status"),
      col("locationid").as("locationid"),
      col("orgname").as("orgname"),
      col("locationids").as("locationids"),
      col("externalid").as("externalid"),
      col("slug").as("slug")).cache();

    val rootOrgs = organisationDF.select(col("id").as("rootorgjoinid"), col("channel").as("rootorgchannel"), col("slug").as("rootorgslug")).where(col("isrootorg") && col("status").===(1)).collect();
    val rootOrgRDD = sparkSession.sparkContext.parallelize(rootOrgs.toSeq);
    val rootOrgEncoder = Encoders.product[RootOrgData].schema
    val rootOrgDF = sparkSession.createDataFrame(rootOrgRDD, rootOrgEncoder);

    val subOrgDF = organisationDF
      .withColumn("explodedlocation", explode(when(size(col("locationids")).equalTo(0), array(lit(null).cast("string")))
        .otherwise(when(col("locationids").isNotNull, col("locationids"))
          .otherwise(array(lit(null).cast("string"))))))

    val subOrgJoinedDF = subOrgDF
      .where(col("status").equalTo(1) && not(col("isrootorg")))
      .join(locationDF, subOrgDF.col("explodedlocation") === locationDF.col("locid"), "left_outer")
      .join(rootOrgDF, subOrgDF.col("rootorgid") === rootOrgDF.col("rootorgjoinid"), "left_outer").as[SubOrgRow]

    subOrgJoinedDF
      .groupBy("slug")
      .agg(countDistinct("id").as("schools"), countDistinct(col("locType").equalTo("district")).as("districts"),
        countDistinct(col("locType").equalTo("block")).as("blocks"))
      .coalesce(1)
      .write
      .partitionBy("slug")
      .mode("overwrite")
      .json(s"$summaryDir")

    val districtDF = subOrgJoinedDF.where(col("loctype").equalTo("district")).select(col("channel").as("channel"), col("slug"), col("id").as("schoolid"), col("orgname").as("schoolname"), col("locid").as("districtid"), col("locname").as("districtname"));

    val blockDF = subOrgJoinedDF.where(col("loctype").equalTo("block")).select(col("id").as("schooljoinid"), col("locid").as("blockid"), col("locname").as("blockname"), col("externalid"));

    val blockData = blockDF.join(districtDF, blockDF.col("schooljoinid").equalTo(districtDF.col("schoolid")), "left").drop(col("schooljoinid")).coalesce(1)
      .select(col("schoolid").as("School id"),
        col("schoolname").as("School name"),
        col("channel").as("Channel"),
        col("districtid").as("District id"),
        col("districtname").as("District name"),
        col("blockid").as("Block id"),
        col("blockname").as("Block name"),
        col("slug").as("slug"),
        col("externalid"))
    blockData
  }
}
