package odbtest

import com.tinkerpop.blueprints.impls.orient.{OrientGraph, OrientGraphFactory, OrientGraphNoTx}
import com.typesafe.scalalogging.slf4j.StrictLogging

/**
  */
class Orient(val factory:OrientGraphFactory) extends StrictLogging {

  def tx[T]  = Orient.tx[T](factory) _

  def noTx[T] = Orient.noTx[T](factory) _

  def close() = factory.close()
}

object Orient {
  def tx[T](factory:OrientGraphFactory)(f: OrientGraph => T) : T = {
    val graph = factory.getTx
    try {
      f(graph)
    } finally {
      graph.shutdown()
    }
  }

  def noTx[T](factory:OrientGraphFactory)(f: OrientGraphNoTx => T) : T = {
    val graph = factory.getNoTx
    try {
      f(graph)
    } finally {
      graph.shutdown()
    }
  }

}
