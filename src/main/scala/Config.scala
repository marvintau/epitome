package epitome

import chisel3._
import chisel3.util._

trait Config {

  val DATA_BITS  = 32
  val DATA_BYTES = DATA_BITS / 8

  val PADDING_LEN = 2
  val VIRT_MEM_ADDR_LEN = 32 

}
