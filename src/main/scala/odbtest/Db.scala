package odbtest

import com.orientechnologies.orient.client.remote.OServerAdmin
import com.typesafe.scalalogging.slf4j.StrictLogging
import odbtest.settings.DbData

/**
  * 1) Деградация при заполнении: изменение характерных времен от объема базы
  * 2) Чтение/запись на заполненной базе: гистограммы распределения характерных времен (перцентили).
 *     Операции: чтение вершины/ребра, создание вершины/ребра
  */

object Db extends StrictLogging {
  def recreateTestDb(db:DbData) = {
    logger.info("Connecting to database...")
    val serverAdmin: OServerAdmin = new OServerAdmin(db.remoteUrl)
      .connect(db.user, db.pass)

    try {
      logger.info("Cluster Details: " + serverAdmin.clusterStatus().toString())
      dropDatabase(db, serverAdmin)
      logger.info("Creating db...")
      serverAdmin.createDatabase(db.dbname, "graph", db.dbtype)
      logger.info("DB created")
    } finally {
      serverAdmin.close()
    }
  }

  def dropDatabase(db: DbData, serverAdmin: OServerAdmin) = {
    try {
      if (serverAdmin.existsDatabase(db.dbtype)) {
        logger.info("Dropping old one...")
        serverAdmin.dropDatabase(db.dbtype)
      }
    } catch {
      case e: Throwable =>
        logger.error("Can't drop database", e)
    }
  }
}
