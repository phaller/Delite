package ppl.delite.runtime.graph.ops

import ppl.delite.runtime.graph.targets.Targets
import ppl.delite.runtime.graph.DeliteTaskGraph

/**
 * Author: Kevin J. Brown
 * Date: Dec 2, 2010
 * Time: 8:11:07 PM
 * 
 * Pervasive Parallelism Laboratory (PPL)
 * Stanford University
 */

class OP_MapReduce(val id: String, func: String, resultType: Map[Targets.Value,String]) extends OP_Executable(resultType) {

  final def isDataParallel = true

  def task = kernelName

  private var kernelName: String = ""

  def setKernelName(name: String) {
    kernelName = name
  }

  def function = func

  /**
   * Since the semantics of MapReduce are to return a T, all chunks are necessarily complete before the final T can be returned
   * Therefore additional chunks do not need edges to consumers
   * Chunks require same dependency & input lists
   */
  def chunk(i: Int): OP_MapReduce = {
    val r = new OP_MapReduce(id+"_"+i, function, Targets.unitTypes(resultType))
    r.dependencyList = dependencyList //lists are immutable so can be shared
    r.inputList = inputList
    for (dep <- getDependencies) dep.addConsumer(r)
    r
  }

  def header(kernel: String, graph: DeliteTaskGraph): OP_Single = {
    val h = new OP_Single(id+"_h", kernel, Map(Targets.Scala->kernel))
    //header assumes all inputs of map
    h.dependencyList = dependencyList
    h.inputList = inputList
    h.addConsumer(this)
    for (dep <- getDependencies) dep.replaceConsumer(this, h)
    //map consumes header, map's consumers remain unchanged
    dependencyList = List(h)
    inputList = List(h)

    graph._ops += (id+"_h") -> h
    h
  }

  def cost = 0
  def size = 0

}
