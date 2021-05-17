package systems.opalia.service.sql.impl

import java.time.Instant
import org.apache.commons.dbcp2.BasicDataSource
import systems.opalia.interfaces.database._
import systems.opalia.interfaces.logging.LoggingService
import systems.opalia.interfaces.soa.Bootable


final class DatabaseServiceBootable(config: BundleConfig,
                                    loggingService: LoggingService)
  extends DatabaseService
    with Bootable[Unit, Unit] {

  private val logger = loggingService.newLogger(classOf[DatabaseService].getName)

  private val dataSource = new BasicDataSource()

  dataSource.setDriverClassName(config.driver)
  dataSource.setUrl(config.url)
  dataSource.setUsername(config.user)
  dataSource.setPassword(config.password)
  dataSource.setMinIdle(config.minIdle)
  dataSource.setMaxIdle(config.maxIdle)
  dataSource.setMaxTotal(config.maxTotal)
  dataSource.setMaxOpenPreparedStatements(config.maxOpenPreparedStatements)
  dataSource.setDefaultTransactionIsolation(config.isolationLevel)

  sys.props("org.jooq.no-logo") = "true"

  def withTransaction[T](block: (QueryFactory) => T): T = {

    val connection = dataSource.getConnection
    val start = Instant.now.toEpochMilli
    val executor = new Executor(logger, connection)

    val result =
      try {

        connection.setAutoCommit(false)

        val result = block(executor.newQueryFactory())

        connection.commit()

        result

      } catch {

        case e: Throwable => {

          connection.rollback()

          throw e
        }

      } finally {

        connection.close()
      }

    val end = Instant.now.toEpochMilli

    logger.trace(s"A transaction was performed in ${end - start} ms.")

    result
  }

  protected def setupTask(): Unit = {
  }

  protected def shutdownTask(): Unit = {

    dataSource.close()
  }
}
