package systems.opalia.service.sql.impl

import java.sql.{Connection, JDBCType, PreparedStatement}
import java.time.{LocalDate, LocalDateTime, LocalTime}
import org.jooq
import org.jooq.impl.DSL
import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap
import scala.collection.mutable
import scala.reflect._
import systems.opalia.interfaces.database._
import systems.opalia.interfaces.json.JsonAst
import systems.opalia.interfaces.logging.Logger


class ConcreteExecutor(logger: Logger,
                       loggerStats: Logger,
                       connection: Connection)
  extends Executor {

  def execute[R <: Result : ClassTag](clause: String, parameters: Map[String, Any]): R = {

    val query = DSL.using(connection).parser().parseQuery(clause)

    try {

      findBinding(query, parameters)

      val sql = query.getSQL()

      logger.debug(s"Apply dialect specific SQL statement: $sql")

      val statement = connection.prepareStatement(sql)

      try {

        setValues(statement, query.getBindValues.asScala.toSeq)

        statement.execute()

        val concreteResult =
          if (classTag[R] == classTag[IgnoredResult]) {

            new IgnoredResult {
            }

          } else {

            val metaData = getMetaData(statement)
            val rows = getValues(statement, metaData)
            val columnNames = metaData.map(x => x._1)
            val statistics = JsonAst.JsonObject(ListMap())

            if (classTag[R] == classTag[SingleResult]) {

              if (rows.length != 1)
                throw new IllegalArgumentException(
                  s"Expect set of rows with cardinality of 1 but ${rows.length} received.")

              new SingleResult {

                def columns: IndexedSeq[String] =
                  columnNames

                def meta: JsonAst.JsonObject =
                  statistics

                def transform[T](f: Row => T): T =
                  rows.map(new ConcreteRow(_)).map(f).head
              }

            } else if (classTag[R] == classTag[SingleOptResult]) {

              if (rows.length > 1)
                throw new IllegalArgumentException(
                  s"Expect set of rows with cardinality of 0 or 1 but ${rows.length} received.")

              new SingleOptResult {

                def columns: IndexedSeq[String] =
                  columnNames

                def meta: JsonAst.JsonObject =
                  statistics

                def transform[T](f: Row => T): Option[T] =
                  rows.map(new ConcreteRow(_)).map(f).headOption
              }

            } else if (classTag[R] == classTag[IndexedSeqResult]) {

              new IndexedSeqResult {

                def columns: IndexedSeq[String] =
                  columnNames

                def meta: JsonAst.JsonObject =
                  statistics

                def transform[T](f: Row => T): IndexedSeq[T] =
                  rows.map(new ConcreteRow(_)).map(f)
              }

            } else if (classTag[R] == classTag[IndexedNonEmptySeqResult]) {

              if (rows.isEmpty)
                throw new IllegalArgumentException(
                  s"Expect set of rows with cardinality greater than 1 but ${rows.length} received.")

              new IndexedNonEmptySeqResult {

                def columns: IndexedSeq[String] =
                  columnNames

                def meta: JsonAst.JsonObject =
                  statistics

                def transform[T](f: Row => T): IndexedSeq[T] =
                  rows.map(new ConcreteRow(_)).map(f)
              }

            } else
              throw new IllegalArgumentException(
                "Unsupported type of result class.")
          }

        concreteResult.asInstanceOf[R]

      } finally {

        statement.close()
      }

    } finally {

      query.close()
    }
  }

  private def findBinding(query: jooq.Query, parameters: Map[String, Any]): Unit = {

    parameters
      .foreach {
        case (key, value) =>

          value match {
            case null => query.bind(key, null)
            case x: Char => query.bind(key, x.toString)
            case x: BigDecimal => query.bind(key, x.underlying)
            case x: LocalDate => query.bind(key, java.sql.Date.valueOf(x))
            case x: LocalTime => query.bind(key, java.sql.Time.valueOf(x))
            case x: LocalDateTime => query.bind(key, java.sql.Timestamp.valueOf(x))
            case x: Seq[_] if (x.forall(_.isInstanceOf[Byte])) =>
              query.bind(key, x.map(_.asInstanceOf[Byte]).toArray)
            case x => query.bind(key, x)
          }
      }
  }

  private def setValues(statement: PreparedStatement, parameters: Seq[Any]): Unit = {

    parameters
      .zipWithIndex.map(x => (x._2 + 1) -> x._1)
      .foreach {
        case (key, value) =>

          value match {
            case null => statement.setNull(key, JDBCType.NULL.getVendorTypeNumber)
            case x: Boolean => statement.setBoolean(key, x)
            case x: Byte => statement.setByte(key, x)
            case x: Short => statement.setShort(key, x)
            case x: Integer => statement.setInt(key, x)
            case x: Long => statement.setLong(key, x)
            case x: Float => statement.setFloat(key, x)
            case x: Double => statement.setDouble(key, x)
            case x: Char => statement.setString(key, x.toString)
            case x: String => statement.setString(key, x)
            case x: BigDecimal => statement.setBigDecimal(key, x.underlying)
            case x: LocalDate => statement.setDate(key, java.sql.Date.valueOf(x))
            case x: LocalTime => statement.setTime(key, java.sql.Time.valueOf(x))
            case x: LocalDateTime => statement.setTimestamp(key, java.sql.Timestamp.valueOf(x))
            case x: java.math.BigDecimal => statement.setBigDecimal(key, x)
            case x: java.sql.Date => statement.setDate(key, x)
            case x: java.sql.Time => statement.setTime(key, x)
            case x: java.sql.Timestamp => statement.setTimestamp(key, x)
            case x: Seq[_] if (x.forall(_.isInstanceOf[Byte])) =>
              statement.setBytes(key, x.map(_.asInstanceOf[Byte]).toArray)
            case x: Array[_] if (x.forall(_.isInstanceOf[Byte])) =>
              statement.setBytes(key, x.map(_.asInstanceOf[Byte]))
            case _ =>
              throw new IllegalArgumentException(
                s"Cannot put unsupported type with value $value (${value.getClass.getName}) at key $key.")
          }
      }
  }

  private def getValues(statement: PreparedStatement,
                        metaData: IndexedSeq[(String, JDBCType)]): Vector[ListMap[String, Any]] = {

    val result = statement.getResultSet
    val buffer = mutable.ArrayBuffer[ListMap[String, Any]]()

    if (result != null) {

      while (result.next()) {

        val seq =
          for (column <- metaData) yield
            column._1 -> (column._2 match {

              case JDBCType.NULL => null

              case JDBCType.BIT => result.getBoolean(column._1)
              case JDBCType.BOOLEAN => result.getBoolean(column._1)

              case JDBCType.TINYINT => result.getByte(column._1)
              case JDBCType.SMALLINT => result.getShort(column._1)
              case JDBCType.INTEGER => result.getInt(column._1)
              case JDBCType.BIGINT => result.getLong(column._1)

              case JDBCType.REAL => result.getFloat(column._1)
              case JDBCType.FLOAT => result.getDouble(column._1)
              case JDBCType.DOUBLE => result.getDouble(column._1)

              case JDBCType.NUMERIC => BigDecimal(result.getBigDecimal(column._1))
              case JDBCType.DECIMAL => BigDecimal(result.getBigDecimal(column._1))

              case JDBCType.DATE => result.getDate(column._1).toLocalDate
              case JDBCType.TIME => result.getTime(column._1).toLocalTime
              case JDBCType.TIMESTAMP => result.getTimestamp(column._1).toLocalDateTime

              case JDBCType.BINARY => result.getBytes(column._1).toSeq
              case JDBCType.VARBINARY => result.getBytes(column._1).toSeq
              case JDBCType.LONGVARBINARY => result.getBytes(column._1).toSeq
              case JDBCType.BLOB => result.getBytes(column._1)

              case JDBCType.CHAR => result.getString(column._1)
              case JDBCType.VARCHAR => result.getString(column._1)
              case JDBCType.LONGVARCHAR => result.getString(column._1)
              case JDBCType.CLOB => result.getString(column._1)

              case JDBCType.NCHAR => result.getString(column._1)
              case JDBCType.NVARCHAR => result.getString(column._1)
              case JDBCType.LONGNVARCHAR => result.getString(column._1)
              case JDBCType.NCLOB => result.getString(column._1)

              case x =>
                throw new IllegalArgumentException(
                  s"Cannot set unsupported JDBC type ${x.getName} for column $column.")
            })

        buffer += ListMap(seq: _*)
      }
    }

    buffer.toVector
  }

  private def getMetaData(statement: PreparedStatement): Vector[(String, JDBCType)] = {

    val metaData = statement.getMetaData

    if (metaData == null)
      Vector.empty
    else {

      (for (i <- 1 to metaData.getColumnCount) yield
        (metaData.getColumnLabel(i).toLowerCase, JDBCType.valueOf(metaData.getColumnType(i)))).toVector
    }
  }
}
