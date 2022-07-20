package epitome.branch

import chisel3._
import chisel3.util._

class ReturnAddressStack(val entryCount: Int) extends Module {

  val io = IO(new Bundle {

    val in = new PredBundle()

    val out = new Bundle {
      val target = Output(UInt(32.W))
    }
  })

  val mem = Mem(entryCount, UInt(32.W))
  val sp = Counter(entryCount)

  // Simple return stack mechanism:
  // 
  // If a CALL instruction is identified, push the instruction NEXT
  // to the CALL onto the stack. Otherwise if a RET is identified,
  // pop the return address out and send to output.
  
  val fb = io.in.fb

  when (fb.valid) {
    when (fb.fuOpType === ArithLogic.call) {

      mem.write(sp.value + 1.U, fb.pc + 4.U)
      sp.value := sp.value + 1.U
    
    }.elsewhen (fb.fuOpType === ArithLogic.ret) {
      
      sp.value := sp.value - 1.U

    }
  }

  io.out.target := RegEnable(mem.read(sp.value), io.pc.valid)

}
