package systems.opalia.service.sql.impl

import java.sql.Connection
import java.time.Instant
import systems.opalia.interfaces.database._
import systems.opalia.interfaces.logging.Logger


final class TransactionalImpl(logger: Logger,
                              loggerStats: Logger,
                              connection: Connection)
  extends Transactional {

  def withTransaction[T](block: (Executor) => T): T = {

    val start = Instant.now.toEpochMilli

    val result =
      try {

        connection.setAutoCommit(false)

        val result = block(new ConcreteExecutor(connection))

        connection.commit()

        result

      } catch {

        case e: Throwable => {

          connection.rollback()

          throw e
        }
      }

    val end = Instant.now.toEpochMilli

    loggerStats.debug(s"A transaction was performed in ${end - start} ms.")

    result
  }
}
