name := "spark-csv-db-loader"

version := "0.0.1"

scalaVersion := "2.11.8"

mainClass in Compile := Some("org.tests.FromCSVtoDB")

lazy val sparkVersion = "2.3.1"

lazy val mysqlVersion = "8.0.12"

lazy val configVersion = "1.3.2"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-sql" % sparkVersion % "provided",
  "mysql" % "mysql-connector-java" % mysqlVersion,
  "com.typesafe" % "config" % configVersion
)

resolvers += Resolver.mavenLocal
