package odbtest

import java.nio.file.Paths
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicLong
import java.{lang, util}

import com.google.common.collect.ImmutableMap
import com.orientechnologies.orient.core.command.OCommandRequest
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.tinkerpop.blueprints.{Parameter, Vertex}
import com.tinkerpop.blueprints.impls.orient.{OrientGraph, OrientGraphFactory, OrientVertex}
import com.typesafe.scalalogging.StrictLogging
import org.nohope.test.stress._
import odbtest.settings._
import org.nohope.test.stress.actions.Scenario
import org.nohope.test.stress.functors.{Call, Get}
import org.nohope.test.stress.result.ExportingInterpreter
import org.nohope.test.stress.result.simplified.SimpleInterpreter


import scala.util.Random
import scala.util.control.NonFatal

/**
1000 request /sec 50/50 read/write
size of data 512b of 1 request graphs
5-10 branches of the graph
Primary load testing unit is a patient.
One patient graph node data size is 512 bytes.
One patient graph varies between 5 and 10 child nodes.
Child should be in 2-5 levels
Randomly generated database of 2 millions.
(if disk space will allow that, if not then lower but enough to do random read/write tests)
Production architecture deployment for OrientDB
  */


object Tester extends App with StrictLogging {
  var vertexCC: ThreadLocal[AtomicLong] = null

  override def main(args: Array[String]) = {
    val db = new LoadData
    val settings = new LoadSettings

    vertexCC = new ThreadLocal[AtomicLong] {
      override def initialValue() = {
        new AtomicLong(0)
      }
    }

    logger.info("Supported args: db, schema, fill, rw")
    logger.info("Supported db properties: host, dbname, uri, user, pass, threads, dbtype, pool")
    logger.info("Supported count properties: initialHospitals, initialCompanies, initialDoctorsPerHospital, initialPatients, initialVisits")


    logger.info(s"Db: $db")
    logger.info(s"Settings: $settings")

    val argsSet: Set[String] = args.toSet
    if (argsSet.contains("db")) {
      Db.recreateTestDb(db)
    }
    if (argsSet.contains("schema")) {
      schema(db, settings)
    }
    if (argsSet.contains("fill")) {
      prefill(db, settings)
    }
    if (argsSet.contains("rw")) {
      performTesting(db, settings)
    }

  }

  def schema(db: DbData, settings: TestSettings) = {
    val factory = new OrientGraphFactory(db.remoteUrl, db.user, db.pass).setupPool(db.poolSize, db.poolSize)
    val generator = new Generator

    logger.info("Going to create schema...")
    val orient = new Orient(factory)
    try {
      val setupElementsResult =
        StressTest
          .prepare(1, 1, new Scenario[MeasureProvider] {
          override def doAction(p: MeasureProvider) = {
            p.call("setup_elements", new Call() {
              override def call() = setupElements(orient)
            })
          }
        })
          .perform()
          .interpret(new SimpleInterpreter)

      logger.info(s"Element setup: ${setupElementsResult.getRuntime}s")
    }
  }


  def prefill(db: DbData, settings: TestSettings) = {
    val factory = new OrientGraphFactory(db.remoteUrl, db.user, db.pass).setupPool(db.poolSize, db.poolSize)
    val generator = new Generator

    logger.info(s"Filling database with: ${
      (settings.initialCompanies + settings.initialHospitals * (1 + settings.initialDoctorsPerHospital)
        + settings.initialPatients * (settings.initialVisits + 1)) * db.concurrency
    } elements")

    val orient = new Orient(factory)
    try {
      val prefillResult =
        StressTest
          .prepare(db.concurrency, 1, new Scenario[MeasureProvider] {
          override def doAction(p: MeasureProvider) = {
            p.call("initial_setup", new Call {
              override def call() = {
                performInitialSetup(p.getOperationNumber, db.concurrency, orient, generator, settings)
              }
            })
          }
        }).perform().interpret(new SimpleInterpreter, new ExportingInterpreter(Paths.get("prefill")))
      logger.info(s"Initial setup results:\n$prefillResult")
    }
  }

  def performTesting(db: DbData, settings: TestSettings) = {
    val factory = new OrientGraphFactory(db.remoteUrl, db.user, db.pass).setupPool(db.poolSize, db.poolSize)
    val generator = new Generator

    val orient = new Orient(factory)
    try {
      val random = new Random(ThreadLocalRandom.current)
      val patientFeedback = Array("liked", "disliked").toList

      class RandomValues {
        val pBlockId = random.nextInt(db.concurrency)
        val pId = random.nextInt(settings.initialPatients / db.concurrency)

        val vId = random.nextInt(settings.initialVisits)

        val hBlockId = random.nextInt(db.concurrency)
        val hId = random.nextInt(settings.initialHospitals / db.concurrency)

        val dId = random.nextInt(settings.initialDoctorsPerHospital)

        val cBlockId = random.nextInt(db.concurrency)
        val cId = random.nextInt(settings.initialCompanies / db.concurrency)

        val props = generator.generateInsuarance()

        val visitFeedback: String = random.shuffle(patientFeedback).head
      }


      val selectPatient = new OCommandSQL("select from patient where name = :name")
      val selectVisit = new OCommandSQL("select from visit where name = :name")
      val selectHospital = new OCommandSQL("select from hospital where name = :name")
      val selectDoctor = new OCommandSQL("select from doctor where name = :name")
      val selectCompany = new OCommandSQL("select from insuarance_company where name = :name")

      val setupRelations =
        StressTest
          .prepare(db.concurrency, db.opsPerThread, new Scenario[MeasureProvider] {

          override def doAction(p: MeasureProvider) = {
            if (p.getOperationNumber % 500 == 0) {
              logger.info(s"Operation: ${p.getOperationNumber} / ${db.opsPerThread}")
            }

            orient.tx { graph =>
              try {
                val values = p.get("prepare_random_ids", new Get[RandomValues] {
                  override def get() = new RandomValues()
                })

                val (patientRequest, visitRequest, hospitalRequest, doctorRequest, companyRequest) = p.get("prepare_queries", new Get[(OCommandRequest, OCommandRequest, OCommandRequest, OCommandRequest, OCommandRequest)] {
                  override def get() = {
                    val patientRequest = graph.command(selectPatient)
                    val visitRequest = graph.command(selectVisit)
                    val hospitalRequest = graph.command(selectHospital)
                    val doctorRequest = graph.command(selectDoctor)
                    val companyRequest = graph.command(selectCompany)

                    (patientRequest, visitRequest, hospitalRequest, doctorRequest, companyRequest)
                  }
                })

                val (patient, visit, hospital, doctor, company) = p.get("perform_selections", new Get[(OrientVertex, OrientVertex, OrientVertex, OrientVertex, OrientVertex)] {
                  override def get() = {
                    try {
                      (getVertex(patientRequest, ImmutableMap.of("name", s"${settings.prefix}_patient_${values.pId}_${values.pBlockId}"))
                        , getVertex(visitRequest, ImmutableMap.of("name", s"${settings.prefix}_patient_${values.pId}_${values.pBlockId}_visit_${values.vId}"))
                        , getVertex(hospitalRequest, ImmutableMap.of("name", s"${settings.prefix}_hospital_${values.hId}_${values.hBlockId}"))
                        , getVertex(doctorRequest, ImmutableMap.of("name", s"${settings.prefix}_doctor_${values.dId}_from_hospital_${values.hId}_${values.hBlockId}"))
                        , getVertex(companyRequest, ImmutableMap.of("name", s"${settings.prefix}_company_${values.cId}_${values.cBlockId}"))
                        )
                    } catch {
                      case NonFatal(e) =>
                        //logger.error("select error", e)
                        throw e
                    }
                  }
                })

                p.call("perform_writes", new Call {
                  override def call() = {
                    doctor.addEdge("accepted", visit)
                    patient.addEdge(values.visitFeedback, doctor)
                    val agreement = addVertex(graph, s"${settings.prefix}_insuarance_agreement", values.props)
                    company.addEdge("issued", agreement)
                    patient.addEdge("accepted", agreement)

                  }
                })

                p.call("perform_commit", new Call {
                  override def call() = {
                    graph.commit()
                  }
                })
              } catch {
                case NonFatal(e) =>
                  graph.rollback()
                //logger.error(s"TX error", e)
              }
            }

          }
        }).perform().interpret(new SimpleInterpreter, new ExportingInterpreter(Paths.get("rw")))
      logger.info(s"Relations setup results:\n$setupRelations")

      /*import scala.collection.JavaConverters._
      setupRelations.getResults.values().asScala.slice(0, 1).foreach(
        r => r.getErrors.asScala.foreach(_.printStackTrace())
      )*/
    } finally {
      orient.close()
      factory.close()
    }
  }

  def performInitialSetup(opNumber: Int, concurrency: Int, orient: Orient, generator: Generator, settings: TestSettings) = {

    orient.tx { graph =>
      try {
        // initial setup
        logger.info("Generating companies, hospitals and doctors")

        for (x <- 0 to settings.initialCompanies / concurrency) {
          val companyProps = generator.generateCompany()
          companyProps.put("name", s"${settings.prefix}_company_${x}_$opNumber")
          countingAddVertex(graph, "class:insuarance_company", companyProps)
        }
        graph.commit()

        for (x <- 0 to settings.initialHospitals / concurrency) {
          val hospitalProps = generator.generateHospital()
          hospitalProps.put("name", s"${settings.prefix}_hospital_${x}_$opNumber")
          val hospital = countingAddVertex(graph, "class:hospital", hospitalProps)

          for (y <- 0 to settings.initialDoctorsPerHospital) {
            val doctorProps = generator.generateDoctor()
            doctorProps.put("name", s"${settings.prefix}_doctor_${y}_from_hospital_${x}_$opNumber")
            val doctor = countingAddVertex(graph, "class:doctor", doctorProps)
            doctor.addEdge("employed_at", hospital)
          }
          graph.commit()
        }

        for (x <- 0 to settings.initialPatients / concurrency) {
          val patientProps = generator.generatePatient()
          patientProps.put("name", s"${settings.prefix}_patient_${x}_$opNumber")
          val patient = countingAddVertex(graph, "class:patient", patientProps)

          for (y <- 0 to settings.initialVisits) {
            val props = generator.generateVisit()
            props.put("name", s"${settings.prefix}_patient_${x}_${opNumber}_visit_$y")
            val visit = countingAddVertex(graph, "class:visit", props)
            patient.addEdge("has", visit)
          }
          graph.commit()
        }

        logger.info(s"Preparation done; ${vertexCC.get().get()} vertexes inserted")
      } catch {
        case NonFatal(e) =>
          graph.rollback()
          logger.error(s"TX error", e)
      }
    }
  }

  def countingAddVertex(graph: OrientGraph, clazz: String, props: util.Map[String, String]) = {
    val vertex = addVertex(graph, clazz, props)
    vertexCC.get().incrementAndGet()
    vertex
  }

  def addVertex(graph: OrientGraph, clazz: String, props: util.Map[String, String]) = {
    val vertex = graph.addVertex(clazz, null.asInstanceOf[Array[Object]]) // null.asInstanceOf[Array[Object]] is a workaround
    vertex.setProperties[OrientVertex](props) // explicit typing is a workaround
    vertex
  }


  def getVertex(request: OCommandRequest, props: ImmutableMap[String, String]): OrientVertex = {
    import scala.collection.JavaConverters._
    request.execute(props).asInstanceOf[lang.Iterable[OrientVertex]].asScala.head
  }


  def setupElements(orient: Orient): Unit = {
    logger.info("Setup vertex/edges types...")
    val vertexTypes = List("patient",
      "visit",
      "doctor",
      "hospital",
      "insuarance_agreement",
      "insuarance_company",
      "payment_request",
      "payment_acceptance",
      "payment_rejection"
    )
    createSimpleVertexTypes(orient, vertexTypes)
    val edgeTypes = List("employed_at",
      "has",
      "issued",
      "accepted",
      "proceed_payment",
      "on",
      "notified",
      "with",
      "liked",
      "disliked"
    )
    createSimpleEdgeTypes(orient, edgeTypes)


    orient.noTx { graph =>
      //val request = new OCommandSQL("CREATE INDEX :indexname ON :cname (:propertyname by key) unique;")
      //val command: OCommandRequest = graph.command(request)


      vertexTypes.foreach { v =>
        try {
          graph.createKeyIndex("name", classOf[Vertex], new Parameter("class", v), new Parameter("type", "unique"))
          //command.execute(ImmutableMap.of("indexname", s"index-$v-name-key", "cname", "name"))
        } catch {
          case NonFatal(e) => logger.error(s"can't create index $v", e)
        }
      }
    }

    logger.info("Vertex types are set up")
  }

  def createSimpleVertexTypes(orient: Orient, types: List[String]) = {
    orient.noTx {
      graph =>
        types.foreach(graph.createVertexType(_).createProperty("name", OType.STRING))
    }
  }

  def createSimpleEdgeTypes(orient: Orient, types: List[String]) = {
    orient.noTx {
      graph =>
        types.foreach(graph.createEdgeType)
    }
  }

}
