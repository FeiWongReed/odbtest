package odbtest

import org.nohope.test.TRandom

/**
  */
class Generator {
  val random = TRandom.threadLocal()
  private def genMap(): java.util.Map[String, String] = {
    val ret = new java.util.HashMap[String, String]
    for (x <- 0 to 4) {
      ret.put(random.nextString(64, "Aa"), random.nextString(64, "Aa"))
    }
    ret
  }

  def generatePatient() = {
    genMap()
  }

  def generateCompany() = {
    genMap()
  }

  def generateInsuarance() = {
    genMap()
  }


  def generateHospital() = {
    genMap()
  }

  def generateDoctor() = {
    genMap()
  }

  def generateVisit() = {
    genMap()
  }

  def generateNotification() = {
    genMap()
  }

  def generatePayment() = {
    genMap()
  }

  def generateRejection() = {
    genMap()
  }
}
