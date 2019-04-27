package systems.opalia.service.sql.impl

import org.apache.commons.dbcp.BasicDataSource
import systems.opalia.interfaces.database._
import systems.opalia.interfaces.logging.LoggingService
import systems.opalia.interfaces.soa.Bootable


final class DatabaseServiceBootable(config: BundleConfig,
                                    loggingService: LoggingService)
  extends DatabaseService
    with Bootable[Unit, Unit] {

  private val logger = loggingService.newLogger(classOf[DatabaseService].getName)
  private val loggerStats = loggingService.newLogger(s"${classOf[DatabaseService].getName}-statistics")
  private val dataSource = new BasicDataSource()

  dataSource.setDriverClassName(config.driver)
  dataSource.setUrl(config.url)
  dataSource.setUsername(config.user)
  dataSource.setPassword(config.password)

  dataSource.setMinIdle(config.minIdle)
  dataSource.setMaxIdle(config.maxIdle)
  dataSource.setMaxActive(config.maxActive)
  dataSource.setMaxOpenPreparedStatements(config.maxOpenPreparedStatements)

  def newTransactional(): Transactional =
    new TransactionalImpl(logger, loggerStats, dataSource.getConnection)

  def backup(): Unit =
    throw new UnsupportedOperationException("Does not support management of hot backups for SQL databases.")

  protected def setupTask(): Unit = {
  }

  protected def shutdownTask(): Unit = {

    dataSource.close()
  }
}
