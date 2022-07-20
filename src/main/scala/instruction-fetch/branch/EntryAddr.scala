package epitome.branch

import chisel3._
import chisel3.util._
import epitome.opcode._

// EntryAddr
// =========
// EntryAddr is a utility bundle for fetching components of an
// address in entry of cache or buffer, especially used in the
// branch target buffer and other modules in instruction fetch
// stage.

class EntryAddr(val idxLen: Int) extends Bundle with Config {
  
  def TAG_LEN = VIRT_MEM_ADDR_LEN - PADDING_LEN - idxLen 

  val tag = UInt(TAG_LEN.W)
  val idx = UInt(idxLen.W)
  val pad = UInt(PADDING_LEN.W)

  def fromUInt(x: UInt) = x
    .asTypeOf(UInt(VIRT_MEM_ADDR_LEN.W))
    .asTypeOf(this)

  def getTag(x: UInt) = fromUInt(x).tag
  def getIdx(x: UInt) = fromUInt(x).idx
}
