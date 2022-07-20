
package epitome

import chisel3._
import chisel3.util._

import epitome.bus._
import epitome.branch._

// Address of Initial PC
trait ResetVectorProvided {
  val resetVector = 0x80000000L
}

class RedirectIO extends Bundle with Config {
  val valid = Output(Bool())
  
  val target = Output(UInt(VIRT_MEM_ADDR_LEN.W))
  
  // 1: branch mispredict: only need to flush frontend
  // 0: others: flush the whole pipeline
  val rtype = Output(UInt(1.W))
}

class CtrlFlowIO extends Bundle with Config {

  val instr  = Output(UInt(DATA_BITS.W))
  val pcCurr = Output(UInt(VIRT_MEM_ADDR_LEN.W))
  val pcPred = Output(UInt(VIRT_MEM_ADDR_LEN.W))
  
  val redirect = new RedirectIO()
  
  // N/A for this moment
  val exceptionVec = Output(Vec(16, Bool()))
  val intrVec = Output(Vec(12, Bool()))
  val brIdx = Output(UInt(4.W))
  val isRVC = Output(Bool())
  val crossPageIPFFix = Output(Bool())
}


class InstructionFetch extends Module with ResetVectorProvided with Config {

  val io = IO(new Bundle {
    val imem = new BusUncached(userBits = 64, addrBits = VIRT_MEM_ADDR_LEN)
    val out = Decoupled(new CtrlFlowIO())
    val redirect = Flipped(new RedirectIO())
    
    // Two flush signal for different purpose:
    // flushPipe controls the flushing of the pipeline, or part of which. flushBP only
    // responds for Branch prediction.
    val flushPipe = Output(UInt(4.W))
    val flushPred = Output(Bool())
    
    // N/A momentarily
    val ipf = Input(Bool())
  })

  // PC / Program Counter / Instruction Pointer
  // the current and successive one.
  val pcCurr = RegInit(resetVector.U(32.W))
  val pcSucc = pcCurr + 4.U

  val pcWillUpdate = io.redirect.valid || io.imem.req.fire()

  // Predicted next PC
  val branchPred = Module(new Prediction())
  val pcPred = branchPred.io.out.target
  val pcNext = Mux(io.redirect.valid, io.redirect.target, Mux(branchPred.io.out.valid, pcPred, pcSucc))

  // the predicted next PC will be sent back to BranchPrediction for the prediction at
  // next cycle.
  branchPred.io.in.pc.valid := io.imem.req.fire()
  branchPred.io.in.pc.bits  := pcNext
  branchPred.io.in.flush    := io.redirect.valid

  // tik tok
  when (pcWillUpdate) {
    pcCurr := pcNext
  }

  io.flushPipe := Mux(io.redirect.valid, "b1111".U, 0.U)
  io.flushPred := false.B

  io.out.bits := DontCare
  io.out.bits.instr := io.imem.res.bits.dataR
  
  io.imem.res.bits.user.map { case x =>
    io.out.bits.pcCurr := x(2 * VIRT_MEM_ADDR_LEN - 1, VIRT_MEM_ADDR_LEN)
    io.out.bits.pcPred := x(    VIRT_MEM_ADDR_LEN - 1,                 0)
  }

  io.out.valid := io.imem.res.valid && !io.flushPipe(0)
}
