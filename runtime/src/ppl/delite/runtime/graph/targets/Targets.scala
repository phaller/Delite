package ppl.delite.runtime.graph.targets

/**
 * Author: Kevin J. Brown
 * Date: Dec 4, 2010
 * Time: 4:16:29 AM
 * 
 * Pervasive Parallelism Laboratory (PPL)
 * Stanford University
 */

object Targets extends Enumeration {
  val Scala = Value("scala")
  val Cuda = Value("cuda")

  /**
   * Create a Unit-type Map for the set of targets included in the input Map
   */
  def unitTypes(targets: Map[Value,String]): Map[Value,String] = {
    var unitMap = Map[Value,String]()
    for (target <- targets.keys) {
      unitMap += target -> unitType(target)
    }
    unitMap
  }

  /**
   * Creates a Unit-type Map for all targets
   */
  def unitTypes: Map[Value,String] = {
    var unitMap = Map[Value,String]()
    for (target <- values) {
      unitMap += target -> unitType(target)
    }
    unitMap
  }

  /**
   *  Returns the Unit-type for the specified target as a String
   */
  def unitType(target: Value): String = {
    target match {
      case Scala => "Unit"
      case Cuda => "void"
    }
  }

  /*
  def intType(target: Value): String = {
    target match {
      case Scala => "Int"
      case Cuda => "int"
      case JNI => "jint"
      case Cpp => "int"
    }
  }
  */
}
