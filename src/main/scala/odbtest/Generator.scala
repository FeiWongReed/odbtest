package odbtest

import java.util.concurrent.ThreadLocalRandom

import org.nohope.test.TRandom

/**
  */
class Generator {
  val random = TRandom.threadLocal()
  val r = ThreadLocalRandom.current()
  val values = Array.fill(5000) {
    genMap()
  }


  private def genMap(): java.util.Map[String, String] = {
    val ret = new java.util.HashMap[String, String]
    for (x <- 0 to 4) {
      ret.put(random.nextString(64, "Aa"), random.nextString(64, "Aa"))
    }
    ret
  }

  def getValue() = {
    values(r.nextInt(0, values.length))

  }

  def generatePatient() = {
    getValue()
  }

  def generateCompany() = {
    getValue()
  }

  def generateInsuarance() = {
    getValue()
  }


  def generateHospital() = {
    getValue()
  }

  def generateDoctor() = {
    getValue()
  }

  def generateVisit() = {
    getValue()
  }

  def generateNotification() = {
    getValue()
  }

  def generatePayment() = {
    getValue()
  }

  def generateRejection() = {
    getValue()
  }
}
