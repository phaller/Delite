package ppl.dsl.optiml

import datastruct.scala.{Vector,Matrix}
import scala.virtualization.lms.common.ScalaOpsPkg
import scala.virtualization.lms.common.{BaseExp, Base}

trait VectorImplOps { this: OptiML =>

  def vector_obj_fromseq_impl[A:Manifest](xs: Rep[Seq[A]]): Rep[Vector[A]]
  def vector_obj_ones_impl(length: Rep[Int]): Rep[Vector[Double]]
  def vector_obj_onesf_impl(length: Rep[Int]): Rep[Vector[Float]]
  def vector_obj_zeros_impl(length: Rep[Int]): Rep[Vector[Double]]
  def vector_obj_zerosf_impl(length: Rep[Int]): Rep[Vector[Float]]
  def vector_obj_rand_impl(length: Rep[Int]): Rep[Vector[Double]]
  def vector_obj_randf_impl(length: Rep[Int]): Rep[Vector[Float]]
  def vector_obj_uniform_impl(start: Rep[Double], step_size: Rep[Double], end: Rep[Double], isRow: Rep[Boolean]): Rep[Vector[Double]]
  def vector_obj_flatten_impl[A:Manifest](pieces: Rep[Vector[Vector[A]]]): Rep[Vector[A]]

  def vector_slice_impl[A:Manifest](v: Rep[Vector[A]], start: Rep[Int], end: Rep[Int]): Rep[Vector[A]]
  def vector_concatenate_impl[A:Manifest](v1: Rep[Vector[A]], v2: Rep[Vector[A]]): Rep[Vector[A]]
  def vector_times_matrix_impl[A:Manifest:Arith](v: Rep[Vector[A]], m: Rep[Matrix[A]]): Rep[Vector[A]]
  def vector_outer_impl[A:Manifest:Arith](v1: Rep[Vector[A]], v2: Rep[Vector[A]]): Rep[Matrix[A]]
  def vector_pprint_impl[A:Manifest](v: Rep[Vector[A]]): Rep[Unit]
  def vector_repmat_impl[A:Manifest](m: Rep[Vector[A]], i: Rep[Int], j: Rep[Int]): Rep[Matrix[A]]
  def vector_trans_impl[A](v: Rep[Vector[A]])(implicit mA: Manifest[A], vA: Manifest[Vector[A]]): Rep[Vector[A]]
  def vector_median_impl[A:Manifest:Ordering](v: Rep[Vector[A]]): Rep[A]
  def vector_filter_impl[A:Manifest](v: Rep[Vector[A]], pred: Rep[A] => Rep[Boolean]): Rep[Vector[A]]
  def vector_partition_impl[A:Manifest](v: Rep[Vector[A]], pred: Rep[A] => Rep[Boolean]): (Rep[Vector[A]],Rep[Vector[A]])
  def vector_contains_impl[A:Manifest](v: Rep[Vector[A]], elem: Rep[A]): Rep[Boolean]
  def vector_distinct_impl[A:Manifest](v: Rep[Vector[A]]): Rep[Vector[A]]
}

trait VectorImplOpsStandard extends VectorImplOps {
  this: OptiML =>

  private val base = "ppl.dsl.optiml"


  //////////////////////////
  // kernel implementations

  // TODO: support for asserts and other debugging tools

  def vector_obj_fromseq_impl[A:Manifest](xs: Rep[Seq[A]]) = {
    val v = Vector[A](xs.length, true)
    for (i <- 0 until xs.length) {
      v(i) = xs(i)
    }
    v
  }

  def vector_obj_ones_impl(length: Rep[Int]) = Vector[Double](length, true) mmap { e => 1. }

  def vector_obj_onesf_impl(length: Rep[Int]) = Vector[Float](length, true) mmap { e => 1f }

  def vector_obj_zeros_impl(length: Rep[Int]) = Vector[Double](length, true)

  def vector_obj_zerosf_impl(length: Rep[Int]) = Vector[Float](length, true)

  def vector_obj_rand_impl(length: Rep[Int]) = Vector[Double](length, true) mmap { e => random[Double] }

  def vector_obj_randf_impl(length: Rep[Int]) = Vector[Float](length, true) mmap { e => random[Float] }

  def vector_obj_uniform_impl(start: Rep[Double], step_size: Rep[Double], end: Rep[Double], isRow: Rep[Boolean]) = {
    val length = Math.ceil((end-start)/step_size).asInstanceOfL[Int]
    val out = Vector[Double](length, true)
    for (i <- 0 until length) {
      // TODO: i*step_size (int*double returning double) doesn't work yet (needs to chain 2 implicits: intToDouble, repArithToArithOps)
      out(i) = step_size*i + start
    }
    out
  }

  def vector_obj_flatten_impl[A:Manifest](pieces: Rep[Vector[Vector[A]]]) = {
    if (pieces.length == 0){
      Vector[A](0, pieces.isRow)
    }
    else {
      val sizes = pieces map { e => e.length }
      val (total,begins) = vector_precumulate[Int](sizes, 0)((_: Rep[Int]) + (_: Rep[Int]))
      val result = Vector[A](total, pieces.isRow)
      for (i <- 0 until pieces.length) {
        result.copyFrom(begins(i), pieces(i))
      }
      result
    }
  }

  private def vector_precumulate[A:Manifest](v: Rep[Vector[A]], identity: Rep[A])(func: (Rep[A],Rep[A]) => Rep[A]): (Rep[A], Rep[Vector[A]]) = {
    if (v.length == 0) returnL((identity,Vector[A](0,v.isRow)))
    val result = Vector[A](0, v.isRow)
    var accum = identity
    var i = unit(0)
    while (i < v.length) {
      result += accum
      accum = func(accum, v(i))
      i += 1
    }
    (accum,result)
  }

  def vector_slice_impl[A:Manifest](v: Rep[Vector[A]], start: Rep[Int], end: Rep[Int]) = {
    //v.chkRange(start, end)
    val out = Vector[A](end-start, v.isRow)
    for (i <- start until end){
      out(i-start) = v(i)
    }
    out
  }

  def vector_concatenate_impl[A:Manifest](v1: Rep[Vector[A]], v2: Rep[Vector[A]]) = {
    if (v1.isRow != v2.isRow) {
      println("error: trying to concatenate row and column vectors")
      // TODo: need an exception throwing mechanism in generated code -- could be External, but needs to accessible from Base
    }
    val out = Vector[A](v1.length+v2.length, v1.isRow)
    for (i <- 0 until v1.length){
      out(i) = v1(i)
    }
    for (i <- 0 until v2.length){
      out(i+v1.length) = v2(i)
    }
    out
  }

  def vector_times_matrix_impl[A:Manifest:Arith](v: Rep[Vector[A]], m: Rep[Matrix[A]]) = {
    //v.chkVecMatAgree(v, m)
    val v_trans = v.t
    m.t.mapRows { a_row => a_row *:* v_trans }
  }

  def vector_outer_impl[A:Manifest:Arith](collA: Rep[Vector[A]], collB: Rep[Vector[A]]) = {
    val out = Matrix[A](collA.length, collA.length)
    for (i <- 0 until collA.length ){
      for (j <- 0 until collB.length ){
        out(i,j) = collA(i)*collB(j)
      }
    }
    out
  }

  def vector_pprint_impl[A:Manifest](v: Rep[Vector[A]]) = {
    if (v.isRow){
      print("[ ")
      for (i <- 0 until v.length){
        print(v(i)); print(" ");
      }
      print("]\\n")
    }
    else{
      for (i <- 0 until v.length){
        print("[")
        print(v(i))
        print(" ]\\n")
      }
    }
  }

  def vector_repmat_impl[A:Manifest](v: Rep[Vector[A]], iRep: Rep[Int], jRep: Rep[Int]) = {
    if (v.isRow) {
      val out = Matrix[A](iRep, jRep*v.length)
      for (col <- (0::jRep*v.length)){
        val colToRep = col % v.length
        var rI = unit(0)
        while(rI < iRep){
          out(rI, col) = v(colToRep)
          rI += 1
        }
      }
      out
    }
    else {
      val out = Matrix[A](iRep*v.length, jRep)
      for (row <- (0::iRep*v.length)){
        val rowToRep = row % v.length
        var cI = unit(0)
        while(cI < jRep){
          out(row, cI) = v(rowToRep)
          cI += 1
        }
      }
      out
    }
  }

  def vector_trans_impl[A](v: Rep[Vector[A]])(implicit mA: Manifest[A], vA: Manifest[Vector[A]]) = {
    val out = Vector[A](v.length, !v.isRow)
    for (i <- 0 until v.length){
      out(i) = v(i)
    }
    out  
  }

  def vector_median_impl[A:Manifest:Ordering](v: Rep[Vector[A]]) = {
    // TODO: this isn't the proper definition of median
    v.sort
    v(v.length / 2)
  }

  def vector_filter_impl[A:Manifest](v: Rep[Vector[A]], pred: Rep[A] => Rep[Boolean]) = {
    val result = Vector[A](0, v.isRow)
    for (i <- 0 until v.length) {
      val x = v(i)
      if (pred(x)) result += x
    }

    result
  }

  def vector_partition_impl[A:Manifest](v: Rep[Vector[A]], pred: Rep[A] => Rep[Boolean]) = {
    val resultT = Vector[A](0, v.isRow)
    val resultF = Vector[A](0, v.isRow)
    for (i <- 0 until v.length) {
      val x = v(i)
      (if (pred(x)) resultT else resultF) += x
    }

    (resultT, resultF)
  }

  def vector_contains_impl[A:Manifest](v: Rep[Vector[A]], elem: Rep[A]): Rep[Boolean] = {
    var i = unit(0)
    while (i < v.length) {
      if (v(i) == elem) return true
      i += 1
    }
    return false
  }

  def vector_distinct_impl[A:Manifest](v: Rep[Vector[A]]) = {
    val result = Vector[A](0, v.isRow)
    var i = unit(0)
    while (i < v.length) {
     if (!result.contains(v(i))) result += v(i)
     i += 1
    }
    result
  }

}

trait VectorImplOpsBLAS extends VectorImplOpsStandard { this: OptiML =>
  
  //override def vector_obj_plus_impl = External(..)
}