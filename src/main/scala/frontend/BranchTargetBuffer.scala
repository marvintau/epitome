package nutshell.epitome.frontend 

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


// FIXME: TableAddr
// ================
// excerpted from original NutShell source code. 
// https://github.com/OSCPU/NutShell/blob/master/src/main/scala/nutcore/frontend/BPU.scala#L26-L47
//
// this Bundle acts as a configuration set, some parameters are calculated
// during instantiating modules. Currently we are reproducing the embedded
// version of NutShell, and corresponding parameters are pasted in directly.
// A completed version is expected to be done within a few months.
//
// Y.M.T. 6/28/2022

class Conf(val idxBits: Int) extends Bundle {
  //val padLen = if (Settings.get("IsRV32") || !Settings.get("EnableOutOfOrderExec")) 2 else 3
  
  val padLen = 2
  val VAddrBits = 32

  def tagBits = VAddrBits - padLen - idxBits

  val tag = UInt(tagBits.W)
  val addr = UInt(idxBits.W)
  val padding = UInt(padLen.W)

  def fromUInt(x: UInt) = x.asTypeOf(UInt(VAddrBits.W)).asTypeOf(this)
  def getTag(x: UInt) = fromUInt(x).tag
  def getAddr(x: UInt) = fromUInt(x).addr
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

class UpdateReq extends Bundle {
  
  // FIXME:
  val VAddrBits = 32

  val valid = Output(Bool())

  val pc          = Output(UInt(VAddrBits.W))
  val missed      = Output(Bool())
  val target      = Output(UInt(VAddrBits.W))
  val fuOpType    = Output(FuOpType())
  val branchType  = Output(BranchType())
  val branchTaken = Output(Bool())  // for branch
  val compressed  = Output(Bool()) // for ras, save PC+2 to stack if is RVC
}

class BranchTargetBuffer(sets: Int, conf: Conf) extends Module with Formal{

  val entryType = new Bundle {
    val tag        = UInt(conf.tagBits.W)
    val branchType = UInt(2.W)
    val target     = UInt(32.W)
  }

  val io = new Bundle {
    val flush = Input(Bool())

    val in = new Bundle {
      val pc = Flipped(Valid(UInt(32.W)))
      val req = Flipped(new UpdateReq()) 
    }
    
    val out = new Bundle {
      val hit = Output(Bool())
      val entry = Output(entryType)
    }
  }

  val flush = BoolStopWatch(io.flush, io.in.pc.valid, startHighPriority = true)

  val btb = Module(new Simplex(entryType, sets = sets))
  btb.io.r.req.valid := io.in.pc.valid
  btb.io.r.req.bits.addr := conf.getAddr(io.in.pc.bits)

  io.out.entry := btb.io.r.res.data(0)

  val prevPc = RegEnable(io.in.pc.bits, io.in.pc.valid)
  io.out.hit := io.out.entry.tag === conf.getTag(prevPc) && !flush && RegNext(btb.io.r.req.ready, init = false.B)

  val req = io.in.req
  val btbWrite = WireInit(0.U.asTypeOf(entryType))

  btbWrite.tag := conf.getTag(req.pc)
  btbWrite.target := req.pc
  btbWrite.branchType := req.branchType

  btb.io.w.req.valid := req.missed && req.valid
  btb.io.w.req.bits.addr := conf.getAddr(req.pc)
  btb.io.w.req.bits.data := btbWrite

	// Formal Verification
	// ========================
	when (io.flush) {
		assert(flush)	
	}.elsewhen(io.in.pc.valid) {
		assert(!flush)
	}
  

} 


import chisel3.stage.ChiselStage
object BranchTargetBuffer extends App {
  //(new ChiselStage).emitVerilog(new Simplex(UInt(8.W), 8))
}
