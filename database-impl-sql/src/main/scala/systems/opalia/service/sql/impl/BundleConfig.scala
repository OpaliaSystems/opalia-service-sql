package systems.opalia.service.sql.impl

import com.typesafe.config.Config
import java.sql.Connection
import systems.opalia.commons.configuration.ConfigHelper._
import systems.opalia.commons.configuration.Reader._


final class BundleConfig(config: Config) {

  val driver: String = config.as[String]("database.jdbc.driver")
  val url: String = config.as[String]("database.jdbc.connection.url")

  val user: String = config.as[String]("database.jdbc.connection.user")
  val password: String = config.as[String]("database.jdbc.connection.password")

  val minIdle: Int = config.as[Int]("database.jdbc.connection.min-idle")
  val maxIdle: Int = config.as[Int]("database.jdbc.connection.max-idle")
  val maxTotal: Int = config.as[Int]("database.jdbc.connection.max-total")
  val maxOpenPreparedStatements: Int = config.as[Int]("database.jdbc.connection.max-open-prepared-statements")

  val isolationLevel: Int =
    config.as[String]("database.jdbc.connection.isolation-level").toUpperCase match {
      case "NONE" => Connection.TRANSACTION_NONE
      case "READ_UNCOMMITTED" => Connection.TRANSACTION_READ_UNCOMMITTED
      case "READ_COMMITTED " => Connection.TRANSACTION_READ_COMMITTED
      case "REPEATABLE_READ" => Connection.TRANSACTION_REPEATABLE_READ
      case "SERIALIZABLE" => Connection.TRANSACTION_SERIALIZABLE
      case _ => throw new IllegalArgumentException("Expect valid isolation level.")
    }

  if (minIdle < 1 || maxIdle < 1 || maxTotal < 1 || maxOpenPreparedStatements < 1)
    throw new IllegalArgumentException("Expect connection pool limits greater than one.")
}
