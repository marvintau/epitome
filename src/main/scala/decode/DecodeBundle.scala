package epitome.decode

import chisel3._
import chisel3.util._

trait InstrType {
  def InstrN  = "b0000".U
  def InstrI  = "b0100".U
  def InstrR  = "b0101".U
  def InstrS  = "b0010".U
  def InstrB  = "b0001".U
  def InstrU  = "b0110".U
  def InstrJ  = "b0111".U
  def InstrA  = "b1110".U
  def InstrSA = "b1111".U // Atom Inst: SC

  def isrfWen(instrType : UInt): Bool = instrType(2)
}

object Instructions extends HasInstrType with HasNutCoreParameter {

  def NOP = 0x00000013.U

  val DecodeDefault = List(InstrN, FuType.csr, CSROpType.jmp)

  def DecodeTable = RVIInstr.table ++ NutCoreTrap.table ++
    (if (HasMExtension) RVMInstr.table else Nil) ++
    (if (HasCExtension) RVCInstr.table else Nil) ++
    Priviledged.table ++
    RVAInstr.table ++
    RVZicsrInstr.table ++ RVZifenceiInstr.table
}
