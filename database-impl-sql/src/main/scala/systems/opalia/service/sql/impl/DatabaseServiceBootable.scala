package systems.opalia.service.sql.impl

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

  def newTransactional(): Transactional =
    new TransactionalImpl(logger, dataSource.getConnection)

  def backup(): Unit =
    throw new UnsupportedOperationException("Does not support management of hot backups for SQL databases.")

  protected def setupTask(): Unit = {
  }

  protected def shutdownTask(): Unit = {

    dataSource.close()
  }
}
