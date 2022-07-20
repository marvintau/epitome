package epitome.branch

import chisel3._
import chisel3.util._
import nutshell.epitome.mem.sram._

import chiselFv._

// NOTE: BranchTargetBuffer
// ========================
// BranchTargetBuffer is excerpted from the BPU part of the origianl NutShell. We made several
// modifications:
//
// 1. It was not a module. Now we make it one.
//
// 2. The update request was not transferred through standard IO port (by "boring in"). Now we
//    make it is.

object BoolStopWatch {
  def apply(start: Bool, stop: Bool, startHighPriority: Boolean = false) = {
    val r = RegInit(false.B)
    if (startHighPriority) {
      when (stop) { r := false.B }
      when (start) { r := true.B }
    }
    else {
      when (start) { r := true.B }
      when (stop) { r := false.B }
    }
    r
  }
}

class TargetBuffer(sets: Int, addr: EntryAddr) extends Module with Formal{

  val entryType = new Bundle {
    val tag        = UInt(addr.TAG_LEN.W)
    val branchType = UInt(2.W)
    val target     = UInt(32.W)
  }

  val io = IO(new Bundle {

    val in = new PredBundle()

    val out = Output(entryType)
    val hit = Output(Bool())
  })

  // Define the flush signal. BTB will be set flush when io.flush fired, until
  // next valid pc comes in.

  val flush = BoolStopWatch(io.in.flush, io.in.pc.valid, startHighPriority = true)

  // branch target buffer is just a SRAM. When a valid PC comes in, make a read
  // request from the buffer, trying to fetch a record with given address from
  // the incoming PC.

  val mem = Module(new Simplex(entryType, sets = sets))

  // After sending the result to io.out.entry, it would take a few cycles to get
  // the result of execution, telling if the branch prediction is correct. If not,
  // the corresponding entry in the buffer will be updated.
  //
  // The detail of fb found in Feedback.scala.

  val fb = io.in.fb
  val written = WireInit(0.U.asTypeOf(entryType))

  written.tag := conf.getTag(fb.pc)
  written.target := fb.pc
  written.branchType := fb.branchType

  mem.io.w.req.valid := fb.missed && fb.valid
  mem.io.w.req.bits.addr := conf.getAddr(fb.pc)
  mem.io.w.req.bits.data := written

  // Read out the buffer entry from the SRAM.

  mem.io.r.req.valid := io.in.pc.valid
  mem.io.r.req.bits.addr := conf.getAddr(io.in.pc.bits)

  // setup output place. The response data from SRAM will be stored here.

  io.out := btb.io.r.res.data(0)

  // store a copy of incoming PC. If the tag of incoming PC is equal to the read
  // one, then hit. It also need to meet the condition that we are not in flushing,
  // AND we are reading from the buffer. Otherwise it could be an occassional hit,
  // since the entry could be the previous read.
  
  val prevPc = RegEnable(io.in.pc.bits, io.in.pc.valid)
  
  io.hit := !flush 
    && io.out.tag === conf.getTag(prevPc) 
    && RegNext(btb.io.r.req.ready, init = false.B)

} 

