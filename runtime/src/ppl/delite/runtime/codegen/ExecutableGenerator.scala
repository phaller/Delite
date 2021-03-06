package ppl.delite.runtime.codegen

import ppl.delite.runtime.graph.ops._
import ppl.delite.runtime.scheduler.PartialSchedule
import java.util.ArrayDeque
import collection.mutable.{ArrayBuffer, HashSet}

/**
 * Author: Kevin J. Brown
 * Date: Oct 26, 2010
 * Time: 8:19:19 PM
 *
 * Pervasive Parallelism Laboratory (PPL)
 * Stanford University
 */

/**
 * Generates optimized DeliteExecutable for a given schedule
 * This generator creates a single executable function for each resource based on the provided schedule
 * The resulting executable should have minimal indirection and overhead, as well as increase compiler optimization opportunities
 *
 * This generator makes the following synchronization optimizations:
 * 1) it generates a synchronized getter for dependencies from other resources for the first use only
 *    outputs created in the current resource or already retrieved from another resource are accessed through local variables
 * 2) it generates synchronized result publication only for outputs that other resources will need to consume
 *    outputs that the scheduler has restricted to this resource are kept local 
 */

abstract class ExecutableGenerator {

  def makeExecutables(schedule: PartialSchedule, kernelPath: String) {
    for (i <- 0 until schedule.numResources) {
      ScalaCompile.addSource(makeExecutable(schedule(i), i, kernelPath))
    }
  }

  protected def makeExecutable(resource: ArrayDeque[DeliteOP], location: Int, kernelPath: String) = {
    val out = new StringBuilder //the output string
    val syncList = new ArrayBuffer[DeliteOP] //list of ops needing sync added

    //the header
    writeHeader(out, location, kernelPath)

    //the run method
    out.append("def run() {\n")
    addKernelCalls(resource, location, out, new ArrayBuffer[DeliteOP], syncList)
    out.append('}')
    out.append('\n')

    //the sync methods/objects
    addSync(syncList, out)

    //an accessor method for the object
    addAccessor(out)

    //the footer
    out.append('}')
    out.append('\n')

    out.toString
  }

  protected def writeHeader(out: StringBuilder, location: Int, kernelPath: String) {
    out.append("import ppl.delite.runtime.codegen.DeliteExecutable\n") //base trait
    out.append("import java.util.concurrent.locks._\n") //locking primitives
    ExecutableGenerator.writePath(kernelPath, out) //package of scala kernels
    out.append("object Executable")
    out.append(location)
    out.append(" extends DeliteExecutable {\n")
  }

  protected def addKernelCalls(resource: ArrayDeque[DeliteOP], location: Int, out: StringBuilder, available: ArrayBuffer[DeliteOP], syncList: ArrayBuffer[DeliteOP]) {
    val iter = resource.iterator
    while (iter.hasNext) { //foreach op
      val op = iter.next
      //add to available list
      available += op

      if (op.isInstanceOf[OP_Nested]) makeNestedFunction(op, location)

      //get dependencies
      for (dep <- op.getDependencies) { //foreach dependency
        if (!available.contains(dep)) { //this dependency does not yet exist on this resource
          //add to available list
          available += dep
          //write a getter
          writeGetter(dep, out)
        }
      }

      //write the function call:
      writeFunctionCall(op, out)

      //write the setter:
      var addSetter = false
      for (cons <- op.getConsumers) {
        if (cons.scheduledResource != location) addSetter = true //only add setter if output will be consumed by another resource
      }
      if (addSetter) {
        syncList += op //add op to list that needs sync generation
        writeSetter(op, out)
      }
    }
  }

  protected def writeFunctionCall(op: DeliteOP, out: StringBuilder) {
    if (op.task == null) return //dummy op
    out.append("val ")
    out.append(getSym(op))
    out.append(" : ")
    out.append(op.outputType)
    out.append(" = ")
    out.append(op.task)
    out.append('(')
    var first = true
    for (input <- op.getInputs) {
      if (!first) out.append(',') //no comma before first argument
      first = false
      out.append(getSym(input))
    }
    out.append(')')
    out.append('\n')
  }

  protected def writeGetter(dep: DeliteOP, out: StringBuilder) {
    out.append("val ")
    out.append(getSym(dep))
    out.append(" : ")
    out.append(dep.outputType)
    out.append(" = ")
    out.append(executableName)
    out.append(dep.scheduledResource)
    out.append(".get")
    out.append(dep.id)
    out.append('\n')
  }

  protected def executableName: String

  protected def writeSetter(op: DeliteOP, out: StringBuilder) {
    out.append(getSync(op))
    out.append(".set(")
    out.append(getSym(op))
    out.append(')')
    out.append('\n')
  }

  protected def makeNestedFunction(op: DeliteOP, location: Int) {
    op match {
      case c: OP_Condition => new ConditionGenerator(c, location).makeExecutable()
      case w: OP_While => new WhileGenerator(w, location).makeExecutable()
      case v: OP_Variant => new VariantGenerator(v, location).makeExecutable()
      case err => error("Unrecognized nested OP type: " + err.getClass.getSimpleName)
    }
  }

  protected def addSync(list: ArrayBuffer[DeliteOP], out: StringBuilder) {
    for (op <- list) {
      //add a public get method
      writePublicGet(op, out)
      //add a private sync object
      writeSyncObject(op, out)
    }
  }

  protected def writePublicGet(op: DeliteOP, out: StringBuilder) {
    out.append("def get")
    out.append(op.id)
    out.append(" : ")
    out.append(op.outputType)
    out.append(" = ")
    out.append(getSync(op))
    out.append(".get\n")
  }

  protected def writeSyncObject(op: DeliteOP, out: StringBuilder) {
    //the header
    out.append("private object ")
    out.append(getSync(op))
    out.append( " {\n")

    //the state
    out.append("private val numConsumers : Int = ")
    out.append(calculateConsumerCount(op))

    out.append('\n')
    out.append("private var count : Int = 0\n")
    out.append("private val takeIndex = new ThreadLocal[Int]{ override def initialValue = 0 }\n")
    out.append("private var putIndex : Int = 0\n")
    out.append("private var _result : ")
    out.append(op.outputType)
    out.append(" = _\n")

    out.append("private val lock = new ReentrantLock\n")
    out.append("private val notEmpty = lock.newCondition\n")
    out.append("private val notFull = lock.newCondition\n")

    //the getter
    out.append("def get : ")
    out.append(op.outputType)
    out.append(" = { val takeIndex = this.takeIndex.get; val lock = this.lock; lock.lock; try { while (takeIndex == putIndex) { notEmpty.await }; extract(takeIndex) } finally { lock.unlock } }\n")

    out.append("private def extract(takeIndex: Int) : ")
    out.append(op.outputType)
    out.append(" = { val res = _result; this.takeIndex.set(takeIndex + 1); count -= 1; if (count == 0) { _result = null.asInstanceOf[")
    out.append(op.outputType)
    out.append("]; notFull.signal }; res }\n")

    //the setter
    out.append("def set(result : ")
    out.append(op.outputType)
    out.append(") { val lock = this.lock; lock.lock; try { while (count != 0) { notFull.await }; insert(result) } finally { lock.unlock } }\n")

    out.append("private def insert(result: ")
    out.append(op.outputType)
    out.append(") { _result = result; count = numConsumers; putIndex += 1; notEmpty.signalAll }\n")

    //the footer
    out.append('}')
    out.append('\n')
  }

  protected def calculateConsumerCount(op: DeliteOP) = {
    val consumerSet = HashSet.empty[Int]
    for (cons <- op.getConsumers) consumerSet += cons.scheduledResource
    consumerSet -= op.scheduledResource
    consumerSet.size
  }

  protected def getSym(op: DeliteOP): String = {
    "x"+op.id
  }

  protected def getSync(op: DeliteOP): String = {
    "Result"+op.id
  }

  protected def addAccessor(out: StringBuilder) {
    out.append("def self = this\n")
  }

}

object ExecutableGenerator {
  private[codegen] def writePath(kernelPath: String, out: StringBuilder) {
    if (kernelPath == "") return
    out.append("import generated.scala._\n")
    /*
    var begin = 0
    var end = kernelPath.length
    if (kernelPath.startsWith("/")) begin += 1
    if (kernelPath.endsWith("/")) end -= 1
    val packageName = kernelPath.replace('/','.').substring(begin,end)
    out.append(packageName)
    out.append(".scala._\n")
    */
  }
}
