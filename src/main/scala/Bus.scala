package epitome.bus

// More desgin details and principles can be found at
// https://oscpu.gitbook.io/nutshell/xi-tong-she-ji/bus

// This bus is explicitly designed for memory accessing.

sealed abstract class BusBundle extends Bundle with Config

object Command {
  // req
                             //   hit    |    miss
  def R          = "b0000".U //  read    |   refill
  def W          = "b0001".U //  write   |   refill
  def burstR     = "b0010".U //  read    |   refill
  def burstW     = "b0011".U //  write   |   refill
  def lastW      = "b0111".U //  write   |   refill
  def probe      = "b1000".U //  read    | do nothing
  def prefetch   = "b0100".U //  read    |   refill

  // res
  def lastR      = "b0110".U
  def resW       = "b0101".U
  def probeHit   = "b1100".U
  def probeMiss  = "b1000".U

  def apply() = UInt(4.W)
} 

class Request(val userBits: Int = 0, val addrBits: Int = 32, val idBits: Int = 0) extends BusBundle {

  val addr = Output(UInt(addrBits.W))
  val size = Output(UInt(3.W))

  val command = Output(Command())

  val maskW = Output(UInt((DATA_BITS / 8).W))
  val dataW = Output(UInt(DATA_BITS.W))

  val user = if (userBits > 0) Some(Output(UInt(userBits.W))) else None
  val id =   if (idBits > 0)   Some(Output(UInt(idBits.W)))   else None

  def apply(addr: UInt, command: UInt, size: UInt, dataW: UInt, maskW: UInt, user: UInt = 0.U, id: UInt = 0.U) {
    this.addr := addr
    this.command := command
    this.size := size
    this.dataW := dataW
    this.maskW := maskW
    this.user.map(_ := user)
    this.id.map(_ := id)
  }

  def isR()         = !command(0) && !command(3)
  def isW()         = command(0)
  def isBurst()     = command(1)
  def isBurstR()    = command === Command.burstR
  def isSingleW()   = command === Command.W
  def islastW()     = command === Command.lastW
  def isProbe()     = command === Command.probe
  def isPrefetch()  = command === Command.prefetch
}


class Response(val userBits: Int = 0, val idBits: Int = 0) extends BusBundle {

  val command = Output(Command())
  val dataR = Output(UInt(DATA_BITS.W))

  val user = if (userBits > 0) Some(Output(UInt(userBits.W))) else None
  val id =   if (idBits > 0)   Some(Output(UInt(idBits.W)))   else None

  def lastR()       = command === Command.lastR
  def isProbeHit()  = command === Command.probeHit
  def isProbeMiss() = command === Command.probeMiss
  def isWriteResp() = command === Command.writeResp
  def isPrefetch()  = command === Command.prefetch
}

// Uncached
class BusUncached(val userBits: Int = 0, val addrBits: Int = 32, val idBits: Int = 0) extends BusBundle {
  
  val req = Decoupled(new Request(userBits, addrBits, idBits))
  val res = Flipped(Decoupled(new Response(userBits, idBits)))

  def isWrite() = req.valid && req.bits.isWrite()
  def isRead()  = req.valid && req.bits.isRead()
  // def toAXI4Lite() = SimpleBus2AXI4Converter(this, new AXI4Lite, false)
  // def toAXI4(isFromCache: Boolean = false) = SimpleBus2AXI4Converter(this, new AXI4, isFromCache)
  def toMemPort() = SimpleBus2MemPortConverter(this, new MemPortIo(32))

}
