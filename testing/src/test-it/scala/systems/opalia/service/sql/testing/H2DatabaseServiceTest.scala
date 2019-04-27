package systems.opalia.service.sql.testing

import com.typesafe.config._
import java.nio.file.{Files, Paths}
import java.time.{LocalDate, LocalDateTime, LocalTime}
import org.scalatest._
import play.api.libs.json.Json
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import systems.opalia.bootloader.ArtifactNameBuilder._
import systems.opalia.bootloader.{Bootloader, BootloaderBuilder}
import systems.opalia.commons.database.converter.BigDecimalConverter._
import systems.opalia.commons.database.converter.DefaultConverter._
import systems.opalia.commons.database.converter.NativeTypesConverter._
import systems.opalia.commons.database.converter.TimeTypesConverter._
import systems.opalia.commons.io.FileUtils
import systems.opalia.commons.json.JsonAstTransformer
import systems.opalia.interfaces.database._
import systems.opalia.interfaces.soa.osgi.ServiceManager


class H2DatabaseServiceTest
  extends FlatSpec
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with Matchers {

  val testName = "h2-database-service-test"
  val serviceManager = new ServiceManager()

  var bootloader: Bootloader = _
  var transactional: Transactional = _

  override final def beforeAll(): Unit = {

    val testPath = Paths.get("./tmp").resolve(testName)

    if (Files.exists(testPath))
      FileUtils.deleteRecursively(testPath)

    val config =
      ConfigFactory.load(
        s"$testName.conf",
        ConfigParseOptions.defaults(),
        ConfigResolveOptions.defaults().setAllowUnresolved(true)
      )
        .resolveWith(ConfigFactory.parseString(
          s"""
             |base-path = $testPath
           """.stripMargin))

    bootloader =
      BootloaderBuilder.newBootloaderBuilder(config)
        .withCacheDirectory(testPath.resolve("felix-cache").normalize())
        .withBundle("systems.opalia" %% "logging-impl-logback" % "1.0.0")
        .withBundle("systems.opalia" %% "database-impl-sql" % "1.0.0")
        .withBundle("org.osgi" % "org.osgi.util.tracker" % "1.5.2")
        .withBundle("org.osgi" % "org.osgi.util.promise" % "1.1.1")
        .withBundle("org.osgi" % "org.osgi.util.function" % "1.1.0")
        .withBundle("org.osgi" % "org.osgi.util.pushstream" % "1.0.1")
        .withBundle("org.osgi" % "org.osgi.service.component" % "1.4.0")
        .withBundle("org.apache.felix" % "org.apache.felix.scr" % "2.1.16")
        .newBootloader()

    bootloader.setup()

    Await.result(bootloader.awaitUp(), Duration.Inf)

    val databaseService = serviceManager.getService(bootloader.bundleContext, classOf[DatabaseService])
    transactional = databaseService.newTransactional()
  }

  override final def afterAll(): Unit = {

    serviceManager.unregisterServices()
    serviceManager.ungetServices(bootloader.bundleContext)

    bootloader.shutdown()

    Await.result(bootloader.awaitDown(), Duration.Inf)
  }

  override def beforeEach(): Unit = {

    transactional.withTransaction {
      implicit executor =>

        Query(
          """
            |CREATE TABLE _Person (
            |  id INT NOT NULL,
            |  name VARCHAR(120) NOT NULL,
            |  counter INT NOT NULL,
            |  extra VARCHAR(120) NULL,
            |  PRIMARY KEY (id),
            |);
            |
            |CREATE TABLE _Group (
            |  id INT NOT NULL,
            |  name VARCHAR(120) NOT NULL,
            |  PRIMARY KEY (id),
            |);
            |
            |CREATE TABLE _Membership (
            |  id INT NOT NULL,
            |  person_id INT NOT NULL,
            |  group_id INT NOT NULL,
            |  PRIMARY KEY (id),
            |  FOREIGN KEY (person_id) REFERENCES _Person(id),
            |  FOREIGN KEY (group_id) REFERENCES _Group(id),
            |);
            |
            |CREATE TABLE _Relationship (
            |  id INT NOT NULL,
            |  name VARCHAR(120) NOT NULL,
            |  friend BOOL NULL,
            |  person1_id INT NOT NULL,
            |  person2_id INT NOT NULL,
            |  PRIMARY KEY (id),
            |  FOREIGN KEY (person1_id) REFERENCES _Person(id),
            |  FOREIGN KEY (person2_id) REFERENCES _Person(id),
            |);
            |
            |CREATE TABLE _Values (
            |  val_boolean BOOL NOT NULL,
            |  val_byte TINYINT NOT NULL,
            |  val_short SMALLINT NOT NULL,
            |  val_int MEDIUMINT NOT NULL,
            |  val_long BIGINT NOT NULL,
            |  val_float FLOAT4 NOT NULL,
            |  val_double FLOAT8 NOT NULL,
            |  val_char NVARCHAR(1) NOT NULL,
            |  val_string TEXT NOT NULL,
            |  val_bigDecimal NUMBER NOT NULL,
            |  val_localDate DATE NOT NULL,
            |  val_localTime TIME NOT NULL,
            |  val_localDateTime DATETIME NOT NULL,
            |  val_binary BINARY NOT NULL,
            |);
            |
          """.stripMargin)
          .execute[IgnoredResult]()

        Query(
          """
            |INSERT INTO _Person (id, name, counter) VALUES (1, 'Armin', 25);
            |INSERT INTO _Person (id, name, counter) VALUES (2, 'Folker', 28);
            |INSERT INTO _Person (id, name, counter) VALUES (3, 'Lorelei', 88);
            |INSERT INTO _Person (id, name, counter) VALUES (4, 'Berthold', 3);
            |INSERT INTO _Person (id, name, counter) VALUES (5, 'Fritz', 42);
            |INSERT INTO _Person (id, name, counter) VALUES (6, 'Dagmar', 73);
            |INSERT INTO _Person (id, name, counter) VALUES (7, 'Emma', 25);
            |INSERT INTO _Person (id, name, counter) VALUES (8, 'Erhard', 109);
            |
            |INSERT INTO _Group (id, name) VALUES (9, 'Group1');
            |INSERT INTO _Group (id, name) VALUES (10, 'Group2');
            |
            |INSERT INTO _Membership (id, person_id, group_id) VALUES (11, 1, 9);
            |INSERT INTO _Membership (id, person_id, group_id) VALUES (12, 2, 9);
            |INSERT INTO _Membership (id, person_id, group_id) VALUES (13, 3, 9);
            |INSERT INTO _Membership (id, person_id, group_id) VALUES (14, 4, 9);
            |INSERT INTO _Membership (id, person_id, group_id) VALUES (15, 4, 10);
            |INSERT INTO _Membership (id, person_id, group_id) VALUES (16, 5, 10);
            |INSERT INTO _Membership (id, person_id, group_id) VALUES (17, 6, 10);
            |INSERT INTO _Membership (id, person_id, group_id) VALUES (18, 7, 10);
            |
            |INSERT INTO _Relationship (id, name, friend, person1_id, person2_id) VALUES (19, 'KNOWS', TRUE, 1, 2);
            |INSERT INTO _Relationship (id, name, friend, person1_id, person2_id) VALUES (20, 'KNOWS', TRUE, 1, 3);
            |INSERT INTO _Relationship (id, name,         person1_id, person2_id) VALUES (21, 'KNOWS', 1, 4);
            |INSERT INTO _Relationship (id, name, friend, person1_id, person2_id) VALUES (22, 'LOVES', FALSE, 1, 3);
            |INSERT INTO _Relationship (id, name, friend, person1_id, person2_id) VALUES (23, 'KNOWS', TRUE, 3, 4);
            |INSERT INTO _Relationship (id, name,         person1_id, person2_id) VALUES (24, 'LOVES', 3, 4);
            |INSERT INTO _Relationship (id, name, friend, person1_id, person2_id) VALUES (25, 'KNOWS', TRUE, 2, 3);
            |INSERT INTO _Relationship (id, name, friend, person1_id, person2_id) VALUES (26, 'KNOWS', FALSE, 2, 5);
            |INSERT INTO _Relationship (id, name, friend, person1_id, person2_id) VALUES (27, 'KNOWS', TRUE, 4, 6);
            |INSERT INTO _Relationship (id, name, friend, person1_id, person2_id) VALUES (28, 'KNOWS', TRUE, 5, 7);
            |INSERT INTO _Relationship (id, name, friend, person1_id, person2_id) VALUES (29, 'KNOWS', TRUE, 6, 7);
            |INSERT INTO _Relationship (id, name, friend, person1_id, person2_id) VALUES (30, 'KNOWS', TRUE, 7, 8);
          """.stripMargin)
          .execute[IgnoredResult]()
    }
  }

  override def afterEach(): Unit = {

    transactional.withTransaction {
      implicit executor =>

        Query(
          """
            |DROP TABLE _Relationship;
            |DROP TABLE _Membership;
            |DROP TABLE _Group;
            |DROP TABLE _Person;
            |DROP TABLE _Values;
          """.stripMargin)
          .execute[IgnoredResult]()
    }
  }

  it should "be able to make a query with multiple arguments" in {

    transactional.withTransaction {
      implicit executor =>

        val result =
          Query(
            """
              |SELECT P2.name AS name
              |FROM _Person P1, _Person P2
              |INNER JOIN _Membership M1 ON P1.id = M1.person_id
              |INNER JOIN _Group G1 ON M1.group_id = G1.id AND G1.name = 'Group1'
              |INNER JOIN _Membership M2 ON P2.id = M2.person_id
              |INNER JOIN _Group G2 ON M2.group_id = G2.id AND G2.name = 'Group1'
              |INNER JOIN _Relationship R1 ON P1.id = R1.person1_id AND P2.id = R1.person2_id AND R1.name = 'KNOWS'
              |WHERE P1.name = ? or P1.name = ?;
            """.stripMargin)
            .on("1", "Folker")
            .on("2", "Lorelei")
            .execute[IndexedSeqResult]()
            .transform(row => row[String]("name"))

        result.toSet should be(Set("Lorelei", "Berthold"))
    }
  }

  it should "be able to convert result rows to JSON" in {

    transactional.withTransaction {
      implicit executor =>

        val result1 =
          Query(
            """
              |SELECT P2.name AS name, P2.counter AS counter
              |FROM _Person P1, _Person P2
              |INNER JOIN _Membership M1 ON P1.id = M1.person_id
              |INNER JOIN _Group G1 ON M1.group_id = G1.id AND G1.name = 'Group1'
              |INNER JOIN _Relationship R1 ON R1.name = 'LOVES' AND
              |((P1.id = R1.person1_id AND P2.id = R1.person2_id) OR (P1.id = R1.person2_id AND P2.id = R1.person1_id))
              |WHERE P1.name = ?;
            """.stripMargin)
            .on("1", "Armin")
            .execute[SingleResult]()
            .transform(row => row.toJson)

        val result2 =
          Query(
            """
              |SELECT R2.name AS name, R2.friend AS friend
              |FROM _Person P1, _Person P2
              |INNER JOIN _Membership M1 ON P1.id = M1.person_id
              |INNER JOIN _Group G1 ON M1.group_id = G1.id AND G1.name = 'Group1'
              |INNER JOIN _Membership M2 ON P2.id = M2.person_id
              |INNER JOIN _Group G2 ON M2.group_id = G2.id AND G2.name = 'Group2'
              |INNER JOIN _Relationship R1 ON R1.name = 'LOVES' AND
              |((P1.id = R1.person1_id AND P2.id = R1.person2_id) OR (P1.id = R1.person2_id AND P2.id = R1.person1_id))
              |INNER JOIN _Relationship R2 ON R2.name = 'KNOWS' AND
              |((P1.id = R2.person1_id AND P2.id = R2.person2_id) OR (P1.id = R2.person2_id AND P2.id = R2.person1_id))
            """.stripMargin)
            .execute[SingleResult]()
            .transform(row => row.toJson)

        JsonAstTransformer.toPlayJson(result1) should be(
          Json.obj("name" -> "Lorelei", "counter" -> 88)
        )

        JsonAstTransformer.toPlayJson(result2) should be(
          Json.obj("name" -> "KNOWS", "friend" -> true)
        )
    }
  }

  it should "handle optional parameters" in {

    transactional.withTransaction {
      implicit executor =>

        val result1 =
          Query(
            """
              |SELECT P1.name AS name, P1.counter AS counter
              |FROM _Person P1
              |INNER JOIN _Membership M1 ON P1.id = M1.person_id
              |INNER JOIN _Group G1 ON M1.group_id = G1.id AND G1.name = 'Group2'
              |WHERE P1.name = ?;
            """.stripMargin)
            .on("1", "Dagmar")
            .execute[SingleOptResult]()

        val result2 =
          Query(
            """
              |SELECT P1.name AS name, P1.counter AS counter
              |FROM _Person P1
              |INNER JOIN _Membership M1 ON P1.id = M1.person_id
              |INNER JOIN _Group G1 ON M1.group_id = G1.id AND G1.name = 'Group2'
              |WHERE P1.name = ?;
            """.stripMargin)
            .on("1", Some("Dagmar"))
            .execute[SingleOptResult]()

        val result3 =
          Query(
            """
              |SELECT P1.name AS name, P1.counter AS counter
              |FROM _Person P1
              |INNER JOIN _Membership M1 ON P1.id = M1.person_id
              |INNER JOIN _Group G1 ON M1.group_id = G1.id AND G1.name = 'Group2'
              |WHERE P1.name = ?;
            """.stripMargin)
            .on("1", None)
            .execute[SingleOptResult]()

        val result4 =
          Query(
            """
              |SELECT P1.name AS name, P1.extra AS extra
              |FROM _Person P1
              |INNER JOIN _Membership M1 ON P1.id = M1.person_id
              |INNER JOIN _Group G1 ON M1.group_id = G1.id AND G1.name = 'Group2'
              |WHERE P1.name = ?;
            """.stripMargin)
            .on("1", "Dagmar")
            .execute[SingleOptResult]()

        val transformer1 =
          (row: Row) => (row[String]("name"), row[Option[Int]]("counter"))

        val transformer2 =
          (row: Row) => (row[String]("name"), row[Option[String]]("counter"))

        val transformer3 =
          (row: Row) => (row[String]("name"), row[Option[String]]("extra"))

        val transformer4 =
          (row: Row) => (row[String]("name"), row[String]("extra"))

        result1.transform(transformer1) shouldBe Some("Dagmar", Some(73))
        result1.transform(transformer2) shouldBe Some("Dagmar", None)

        result2.transform(transformer1) shouldBe Some("Dagmar", Some(73))
        result2.transform(transformer2) shouldBe Some("Dagmar", None)

        result3.transform(transformer1) shouldBe None
        result3.transform(transformer2) shouldBe None

        result4.transform(transformer3) shouldBe Some("Dagmar", None)

        an[IllegalArgumentException] should be thrownBy result4.transform(transformer4)
    }
  }

  it should "be able to parse a required number of rows" in {

    transactional.withTransaction {
      implicit executor =>

        val queryExpectEmpty =
          Query(
            """
              |SELECT P1.name AS name
              |FROM _Person P1
              |INNER JOIN _Membership M1 ON P1.id = M1.person_id
              |INNER JOIN _Group G1 ON M1.group_id = G1.id AND G1.name = 'Group1'
              |WHERE P1.name = ?;
            """.stripMargin)
            .on("1", "Adam")

        val queryExpectOne =
          Query(
            """
              |SELECT P1.name AS name
              |FROM _Person P1
              |INNER JOIN _Membership M1 ON P1.id = M1.person_id
              |INNER JOIN _Group G1 ON M1.group_id = G1.id AND G1.name = 'Group1'
              |WHERE P1.name = ?;
            """.stripMargin)
            .on("1", "Armin")

        val queryExpectMultiple =
          Query(
            """
              |SELECT P1.name AS name
              |FROM _Person P1
              |INNER JOIN _Membership M1 ON P1.id = M1.person_id
              |INNER JOIN _Group G1 ON M1.group_id = G1.id AND G1.name = 'Group1';
            """.stripMargin)

        val transformer =
          (row: Row) => row[String]("name")

        // single

        an[IllegalArgumentException] should be thrownBy
          queryExpectEmpty.execute[SingleResult]().transform(transformer)

        queryExpectOne.execute[SingleResult]().transform(transformer) shouldBe a[String]

        an[IllegalArgumentException] should be thrownBy
          queryExpectMultiple.execute[SingleResult]().transform(transformer)

        // single optional

        queryExpectEmpty.execute[SingleOptResult]().transform(transformer) shouldBe empty

        queryExpectOne.execute[SingleOptResult]().transform(transformer) shouldBe defined

        an[IllegalArgumentException] should be thrownBy
          queryExpectMultiple.execute[SingleOptResult]().transform(transformer)

        // sequence

        queryExpectEmpty.execute[IndexedSeqResult]().transform(transformer) should have size 0

        queryExpectOne.execute[IndexedSeqResult]().transform(transformer) should have size 1

        queryExpectMultiple.execute[IndexedSeqResult]().transform(transformer) should have size 4

        // non empty sequence

        an[IllegalArgumentException] should be thrownBy
          queryExpectEmpty.execute[IndexedNonEmptySeqResult]().transform(transformer)

        queryExpectOne.execute[IndexedNonEmptySeqResult]().transform(transformer) should have size 1

        queryExpectMultiple.execute[IndexedNonEmptySeqResult]().transform(transformer) should have size 4
    }
  }

  it should "be able to convert native types" in {

    transactional.withTransaction {
      implicit executor =>

        val boolean: Boolean = true
        val byte: Byte = 42
        val short: Short = 1042
        val int: Int = 10042
        val long: Long = 100042
        val float: Float = 0.73f
        val double: Double = 0.073d
        val char: Char = 'Ä'
        val string: String = "test"
        val bigDecimal: BigDecimal = BigDecimal("9182736453733.8376546370383873654670872634998616321013016732842")
        val localDate: LocalDate = LocalDate.now()
        val localTime: LocalTime = LocalTime.now().withNano(0)
        val localDateTime: LocalDateTime = LocalDateTime.now().withNano(0)
        val binary: Seq[Byte] = List(0x00.toByte, 0xFF.toByte, 0xFA.toByte)

        Query(
          """
            |INSERT INTO _Values
            |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
          """.stripMargin)
          .on("1", boolean)
          .on("2", byte)
          .on("3", short)
          .on("4", int)
          .on("5", long)
          .on("6", float)
          .on("7", double)
          .on("8", char)
          .on("9", string)
          .on("10", bigDecimal)
          .on("11", localDate)
          .on("12", localTime)
          .on("13", localDateTime)
          .on("14", binary)
          .execute[IgnoredResult]()

        val result =
          Query(
            """
              |SELECT *
              |FROM _Values
            """.stripMargin)
            .execute[SingleResult]()
            .transform {
              row =>

                (row[Boolean]("val_boolean"),
                  row[Byte]("val_byte"),
                  row[Short]("val_short"),
                  row[Int]("val_int"),
                  row[Long]("val_long"),
                  row[Float]("val_float"),
                  row[Double]("val_double"),
                  row[Char]("val_char"),
                  row[String]("val_string"),
                  row[BigDecimal]("val_bigDecimal"),
                  row[LocalDate]("val_localDate"),
                  row[LocalTime]("val_localTime"),
                  row[LocalDateTime]("val_localDateTime"),
                  row[Seq[Byte]]("val_binary"))
            }

        result._1 should be(boolean)
        result._2 should be(byte)
        result._3 should be(short)
        result._4 should be(int)
        result._5 should be(long)
        result._6 should be(float)
        result._7 should be(double)
        result._8 should be(char)
        result._9 should be(string)

        result._10 should be(bigDecimal)
        result._11 should be(localDate)
        result._12 should be(localTime)
        result._13 should be(localDateTime)
        result._14 should be(binary)
    }
  }
}
