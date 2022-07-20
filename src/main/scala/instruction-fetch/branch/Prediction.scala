package epitome.branch

import chisel3._
import chisel3.util._

class PredBundle extends Bundle {
  val flush = Input(Bool())
  val fb = Flipped(new Feedback())
  val pc = Flipped(Valid(UInt(32.W)))
}

object BranchType {
  def B = "b00".U  // branch
  def J = "b01".U  // jump
  def I = "b10".U  // indirect
  def R = "b11".U  // return

  def apply() = UInt(2.W)
}

object FuOpType {
  def apply() = UInt(7.W)
}


class Prediction extends Module {

  val io = IO(new Bundle{

    val in = new PredBundle()

    val out = new RedirectIO()
  })

  // Put a Branch Target Buffer (BTB) && Pattern History Table (PHT)

  val bufferEntryCount = 512
  val conf = new Conf(log2Up(bufferEntryCount))
  val btb = Module(new TargetBuffer(bufferEntryCount, conf)) 
  val pht = Module(new PatternHistory(bufferEntryCount, conf))

  // Put a Return Address Stack (RAS)
  
  val stackEntryCount = 16
  val ras = Module(new ReturnAddressStack(stackEntryCount))

  // Connect'em all
  
  btb.io.in <> io.in
  pht.io.in <> io.in
  ras.io.in <> io.in

  val returnTarget = ras.io.out.target
  val branchTarget = btb.io.out.target
  val branchType   = btb.io.out.branchType

  val branchTaken  = pht.io.out

  io.out.target := Mux(branchType === BranchType.R, returnTarget, branchTarget)
  io.out.valid := btb.io.hit && Mux(branchType === BranchType.B, branchTaken, true.B)
  io.out.rtype := 0.U
}
