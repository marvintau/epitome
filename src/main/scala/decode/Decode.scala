package epitome.decode

import chisel3._
import chisel3.util._

import epitome._
import epitome.utils._

val RegLookUpTable = List(
  RVCInstr.DtCare   -> 0.U,
  RVCInstr.REGrs    -> rs,
  RVCInstr.REGrt    -> rt,
  RVCInstr.REGrd    -> rd,
  RVCInstr.REGrs1   -> rs1,
  RVCInstr.REGrs2   -> rs2,
  RVCInstr.REGrs1p  -> rs1p,
  RVCInstr.REGrs2p  -> rs2p,
  RVCInstr.REGx1    -> 1.U,
  RVCInstr.REGx2    -> 2.U
)

val SrcTypeTable = List(
  InstrI -> (SrcType.reg, SrcType.imm),
  InstrR -> (SrcType.reg, SrcType.reg),
  InstrS -> (SrcType.reg, SrcType.reg),
  InstrSA-> (SrcType.reg, SrcType.reg),
  InstrB -> (SrcType.reg, SrcType.reg),
  InstrU -> (SrcType.pc , SrcType.imm),
  InstrJ -> (SrcType.pc , SrcType.imm),
  InstrN -> (SrcType.pc , SrcType.imm)
)

object ImmLookup extends Config {

  def apply (instr: UInt(DATA_BITS.W)) = {
    val imm = LookupTree(instrType, List(
      InstrI  -> SignExt(instr(31, 20), DATA_BITS),
      InstrS  -> SignExt(Cat(instr(31, 25), instr(11, 7)), DATA_BITS),
      InstrSA -> SignExt(Cat(instr(31, 25), instr(11, 7)), DATA_BITS),
      InstrB  -> SignExt(Cat(instr(31), instr(7), instr(30, 25), instr(11, 8), 0.U(1.W)), DATA_BITS),
      InstrU  -> SignExt(Cat(instr(31, 12), 0.U(12.W)), DATA_BITS),//fixed
      InstrJ  -> SignExt(Cat(instr(31), instr(19, 12), instr(20), instr(30, 21), 0.U(1.W)), DATA_BITS)
    ))

    imm  
  }

}

object ImmLookupRVC extends Config {

  def apply (instr: UInt(DATA_BITS.W)) = {

  val immrvc = LookupTree(rvcImmType, List(
      // InstrIW -> Cat(Fill(20+32, instr(31)), instr(31, 20)),//fixed
      RVCInstr.ImmNone  -> 0.U(DATA_BITS.W),
      RVCInstr.ImmLWSP  -> ZeroExt(Cat(instr(3,2), instr(12), instr(6,4), 0.U(2.W)), DATA_BITS),
      RVCInstr.ImmLDSP  -> ZeroExt(Cat(instr(4,2), instr(12), instr(6,5), 0.U(3.W)), DATA_BITS),
      RVCInstr.ImmSWSP  -> ZeroExt(Cat(instr(8,7), instr(12,9), 0.U(2.W)), DATA_BITS),
      RVCInstr.ImmSDSP  -> ZeroExt(Cat(instr(9,7), instr(12,10), 0.U(3.W)), DATA_BITS),
      RVCInstr.ImmSW    -> ZeroExt(Cat(instr(5), instr(12,10), instr(6), 0.U(2.W)), DATA_BITS),
      RVCInstr.ImmSD    -> ZeroExt(Cat(instr(6,5), instr(12,10), 0.U(3.W)), DATA_BITS),
      RVCInstr.ImmLW    -> ZeroExt(Cat(instr(5), instr(12,10), instr(6), 0.U(2.W)), DATA_BITS),
      RVCInstr.ImmLD    -> ZeroExt(Cat(instr(6,5), instr(12,10), 0.U(3.W)), DATA_BITS),
      RVCInstr.ImmJ     -> SignExt(Cat(instr(12), instr(8), instr(10,9), instr(6), instr(7), instr(2), instr(11), instr(5,3), 0.U(1.W)), DATA_BITS),
      RVCInstr.ImmB     -> SignExt(Cat(instr(12), instr(6,5), instr(2), instr(11,10), instr(4,3), 0.U(1.W)), DATA_BITS),
      RVCInstr.ImmLI    -> SignExt(Cat(instr(12), instr(6,2)), DATA_BITS),
      RVCInstr.ImmLUI   -> SignExt(Cat(instr(12), instr(6,2), 0.U(12.W)), DATA_BITS),
      RVCInstr.ImmADDI  -> SignExt(Cat(instr(12), instr(6,2)), DATA_BITS),
      RVCInstr.ImmADDI16SP-> SignExt(Cat(instr(12), instr(4,3), instr(5), instr(2), instr(6), 0.U(4.W)), DATA_BITS),
      RVCInstr.ImmADD4SPN-> ZeroExt(Cat(instr(10,7), instr(12,11), instr(5), instr(6), 0.U(2.W)), DATA_BITS),
      RVCInstr.ImmCBREAK -> 1.U(DATA_BITS.W)
      // ImmFLWSP  -> 
      // ImmFLDSP  -> 
    ))

    immrvc
  }

}

class Decoder(implicit val p: NutCoreConfig) extends Module with Config with InstrType {
  
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new CtrlFlowIO()))
    val out = Decoupled(new DecodeIO())

    val isWFI = Output(Bool()) 
    // require NutCoreSim to advance mtime when wfi to reduce the idle time in Linux
  })

  val hasIntr = Wire(Bool())
  val instr = io.in.bits.instr

  val decodeList = ListLookup(instr, Instructions.DecodeDefault, Instructions.DecodeTable)
  
  val instrType :: fuType :: fuOpType :: Nil =
    // insert Instructions.DecodeDefault when interrupt comes
    Instructions.DecodeDefault.zip(decodeList)
      .map{case (intr, dec) => Mux(hasIntr || io.in.bits.exceptionVec(instrPageFault) || io.out.bits.cf.exceptionVec(instrAccessFault), intr, dec)}

  val isRVC = if (HasCExtension) instr(1,0) =/= "b11".U else false.B

  val rvcImmType :: rvcSrc1Type :: rvcSrc2Type :: rvcDestType :: Nil =
    ListLookup(instr, CInstructions.DecodeDefault, CInstructions.CExtraDecodeTable) 

  io.out.bits := DontCare

  io.out.bits.ctrl.fuType := fuType
  io.out.bits.ctrl.fuOpType := fuOpType

  val src1Type = LookupTree(instrType, SrcTypeTable.map(p => (p._1, p._2._1)))
  val src2Type = LookupTree(instrType, SrcTypeTable.map(p => (p._1, p._2._2)))

  val (rs, rt, rd) = (instr(19, 15), instr(24, 20), instr(11, 7))

  // see riscv-spec vol1, Table 16.1: Compressed 16-bit RVC instruction formats.
  val rs1       = instr(11,7)
  val rs2       = instr(6,2)
  val rs1p      = LookupTree(instr(9,7), RVCInstr.RVCRegNumTable.map(p => (p._1, p._2)))
  val rs2p      = LookupTree(instr(4,2), RVCInstr.RVCRegNumTable.map(p => (p._1, p._2)))
  val rvc_shamt = Cat(instr(12),instr(6,2)) 
  // val rdp_rs1p = LookupTree(instr(9,7), RVCRegNumTable)
  // val rdp      = LookupTree(instr(4,2), RVCRegNumTable)

  val rvc_src1 = LookupTree(rvcSrc1Type, RegLookUpTable.map(p => (p._1, p._2)))
  val rvc_src2 = LookupTree(rvcSrc2Type, RegLookUpTable.map(p => (p._1, p._2)))
  val rvc_dest =  LookupTree(rvcDestType, RegLookUpTable.map(p => (p._1, p._2)))

  val rfSrc1 = Mux(isRVC, rvc_src1, rs)
  val rfSrc2 = Mux(isRVC, rvc_src2, rt)
  val rfDest = Mux(isRVC, rvc_dest, rd)
  // TODO: refactor decode logic
  // make non-register addressing to zero, since isu.sb.isBusy(0) === false.B
  io.out.bits.ctrl.rfSrc1 := Mux(src1Type === SrcType.pc, 0.U, rfSrc1)
  io.out.bits.ctrl.rfSrc2 := Mux(src2Type === SrcType.reg, rfSrc2, 0.U)
  io.out.bits.ctrl.rfWen  := isrfWen(instrType)
  io.out.bits.ctrl.rfDest := Mux(isrfWen(instrType), rfDest, 0.U)

  io.out.bits.data := DontCare

  val imm = ImmLookup(instr)
  val immRVC = ImmLookupRVC(instr)
  
  io.out.bits.data.imm  := Mux(isRVC, immRVC, imm)

  when (fuType === FuType.bru) {
    def isLink(reg: UInt) = (reg === 1.U || reg === 5.U)
    when (isLink(rfDest) && fuOpType === ALUOpType.jal) { io.out.bits.ctrl.fuOpType := ALUOpType.call }
    when (fuOpType === ALUOpType.jalr) {
      when (isLink(rfSrc1)) { io.out.bits.ctrl.fuOpType := ALUOpType.ret }
      when (isLink(rfDest)) { io.out.bits.ctrl.fuOpType := ALUOpType.call }
    }
  }
  // fix LUI
  io.out.bits.ctrl.src1Type := Mux(instr(6,0) === "b0110111".U, SrcType.reg, src1Type)
  io.out.bits.ctrl.src2Type := src2Type

  val NoSpecList = Seq(
    FuType.csr
  )

  val BlockList = Seq(
    FuType.mou
  )

  io.out.bits.ctrl.isNutCoreTrap := (instr(31,0) === NutCoreTrap.TRAP) && io.in.valid
  io.out.bits.ctrl.noSpecExec := NoSpecList.map(j => io.out.bits.ctrl.fuType === j).reduce(_ || _)
  io.out.bits.ctrl.isBlocked :=
  (
    io.out.bits.ctrl.fuType === FuType.lsu && LSUOpType.isAtom(io.out.bits.ctrl.fuOpType) ||
    BlockList.map(j => io.out.bits.ctrl.fuType === j).reduce(_ || _)
  )

  //output signals
  io.out.valid := io.in.valid
  io.in.ready := !io.in.valid || io.out.fire() && !hasIntr
  io.out.bits.cf <> io.in.bits

  val intrVec = WireInit(0.U(12.W))
  BoringUtils.addSink(intrVec, "intrVecIDU")
  io.out.bits.cf.intrVec.zip(intrVec.asBools).map{ case(x, y) => x := y }
  hasIntr := intrVec.orR

  val vmEnable = WireInit(false.B)
  BoringUtils.addSink(vmEnable, "DTLBENABLE")

  io.out.bits.cf.exceptionVec.map(_ := false.B)
  io.out.bits.cf.exceptionVec(illegalInstr) := (instrType === InstrN && !hasIntr) && io.in.valid
  io.out.bits.cf.exceptionVec(instrPageFault) := io.in.bits.exceptionVec(instrPageFault)
  if (VAddrBits > PAddrBits) {
    io.out.bits.cf.exceptionVec(instrAccessFault) := io.in.bits.pc(VAddrBits - 1, PAddrBits).orR && !vmEnable
  } else {
    io.out.bits.cf.exceptionVec(instrAccessFault) := false.B
  }

  io.out.bits.ctrl.isNutCoreTrap := (instr === NutCoreTrap.TRAP) && io.in.valid
  io.isWFI := (instr === Priviledged.WFI) && io.in.valid

}

class IDU(implicit val p: NutCoreConfig) extends NutCoreModule with HasInstrType {

  val io = IO(new Bundle {
    val in = Vec(2, Flipped(Decoupled(new CtrlFlowIO)))
    val out = Vec(2, Decoupled(new DecodeIO))
  })

  val decoder1  = Module(new Decoder)
  val decoder2  = Module(new Decoder)

  io.in(0) <> decoder1.io.in
  io.in(1) <> decoder2.io.in
  io.out(0) <> decoder1.io.out
  io.out(1) <> decoder2.io.out

  if(!EnableMultiIssue){
    io.in(1).ready := false.B
    decoder2.io.in.valid := false.B
  }

}
