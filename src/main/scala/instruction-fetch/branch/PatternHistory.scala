package epitome.branch

import chisel3._
import chisel3.util._
import nutshell.epitome.opcode._
import nutshell.epitome.mem.sram

class PatternHistory(val entryCount: Int, val addr: EntryAddr) extends Module {

  val io = IO(new Bundle { 

    val in = new PredBundle()

    val out = Output(Bool())
  })
  
  val mem = Mem(entryCount, UInt(2.W))
  
  val fbDelayed = RegNext(io.in.fb)

  val currCount = RegNext(mem.read(addr.getIdx(io.in.fb.pc)))

  when (fbDelayed.valid && ArithLogic.isBranch(fbDelayed.fuOpType) ) {

    val realTaken = fbDelayed.branchTaken
    val nextCount = Mux(realTaken, currCount + 1.U, currCount - 1.U)
    val enableW = (realTaken && (currCount =/= "b11".U)) || (!realTaken && (currCount =/= "b00".U))

    when (enableW) {
      mem.write(addr.getIdx(fbDelayed.pc), nextCount)
    }
    
  }
  
  io.out := RegEnable(mem.read(addr.getIdx(io.in.pc.bits))(1), io.in.pc.valid)
}


