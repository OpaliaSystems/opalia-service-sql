
val mScalaVersion = "2.12.13"
val mInterfacesVersion = "1.0.0"
val mCommonsVersion = "1.0.0"
val mBootloaderVersion = "1.0.0"
val mCrossVersion = """^(\d+\.\d+)""".r.findFirstIn(mScalaVersion).get

val exclusionRules = Seq(
  ExclusionRule("org.scala-lang", "scala-library"),
  ExclusionRule("org.scala-lang", "scala-reflect"),
  ExclusionRule("org.scala-lang", "scala-compiler"),
  ExclusionRule("com.typesafe", "config"),
  ExclusionRule("systems.opalia", s"interfaces_$mCrossVersion"),
  ExclusionRule("org.osgi", "org.osgi.core"),
  ExclusionRule("org.osgi", "org.osgi.service.component"),
  ExclusionRule("org.osgi", "org.osgi.compendium")
)

def commonSettings: Seq[Setting[_]] = {

  Seq(
    organizationName := "Opalia Systems",
    organizationHomepage := Some(url("https://opalia.systems")),
    organization := "systems.opalia",
    homepage := Some(url("https://github.com/OpaliaSystems/opalia-service-sql")),
    version := "1.0.0",
    scalaVersion := mScalaVersion
  )
}

lazy val `testing` =
  (project in file("testing"))
    .settings(

      name := "testing",

      commonSettings,

      libraryDependencies ++= Seq(
        "systems.opalia" %% "interfaces" % mInterfacesVersion,
        "systems.opalia" %% "commons" % mCommonsVersion,
        "systems.opalia" %% "bootloader" % mBootloaderVersion,
        "org.scalatest" %% "scalatest" % "3.0.7" % "test"
      )
    )

lazy val `database-impl-sql` =
  (project in file("database-impl-sql"))
    .settings(

      name := "database-impl-sql",

      description := "The project provides an implementation for accessing various SQL databases.",

      commonSettings,

      bundleSettings,

      OsgiKeys.privatePackage ++= Seq(
        "systems.opalia.service.sql.impl.*"
      ),

      OsgiKeys.importPackage ++= Seq(
        "scala.*",
        "com.typesafe.config.*",
        "systems.opalia.interfaces.*"
      ),

      libraryDependencies ++= Seq(
        "org.osgi" % "org.osgi.core" % "6.0.0" % "provided",
        "org.osgi" % "org.osgi.service.component.annotations" % "1.4.0",
        "org.osgi" % "org.osgi.service.jdbc" % "1.0.0",
        "systems.opalia" %% "interfaces" % mInterfacesVersion % "provided",
        "systems.opalia" %% "commons" % mCommonsVersion excludeAll (exclusionRules: _*),
        "commons-dbcp" % "commons-dbcp" % "1.4",
        "org.jooq" % "jooq" % "3.12.1",
        "com.h2database" % "h2" % "1.4.199",
        "org.postgresql" % "postgresql" % "42.2.8",
        "org.mariadb.jdbc" % "mariadb-java-client" % "2.5.0",
        "com.microsoft.sqlserver" % "mssql-jdbc" % "7.4.1.jre8"
      )
    )
