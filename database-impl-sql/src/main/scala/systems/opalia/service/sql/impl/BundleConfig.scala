package systems.opalia.service.sql.impl

import com.typesafe.config.Config
import systems.opalia.commons.configuration.ConfigHelper._
import systems.opalia.commons.configuration.Reader._


final class BundleConfig(config: Config) {

  val driver: String = config.as[String]("database.jdbc.driver")
  val url: String = config.as[String]("database.jdbc.connection.url")

  val user: String = config.as[String]("database.jdbc.connection.user")
  val password: String = config.as[String]("database.jdbc.connection.password")

  val minIdle: Int = config.as[Int]("database.jdbc.connection.min-idle")
  val maxIdle: Int = config.as[Int]("database.jdbc.connection.max-idle")
  val maxActive: Int = config.as[Int]("database.jdbc.connection.max-active")
  val maxOpenPreparedStatements: Int = config.as[Int]("database.jdbc.connection.max-open-prepared-statements")

  if (minIdle < 1 || maxIdle < 1 || maxActive < 1 || maxOpenPreparedStatements < 1)
    throw new IllegalArgumentException("Expect connection pool limits greater than one.")
}
