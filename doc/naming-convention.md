NOTE: Naming Convention
=======================

Y.M.T. 6/25/2022

For better understanding and typographic viewing, the code here (and anywhere else) follows
the following conventions:

1. Follow the existing convention:

   For example, the A, AW and R are for Addressing, Addressing/Writing and Reading,
   following AXI4 naming convention. They are primitive IO bundles for building serial
   protocol of accessing SyncReadMem.

2. Regarding number:

   Often we need to specify the amount/capacity/size of a buffer/cache/register bank with a
   number (typically integer). 

   - When we need to specify the capacity in __bit/byte__, we call it __"size"__.

   - When we need to specify the amount of __entries/records__, we call it __"sets"__.

   Other English words are discouraged, including:

   - "num*/nr*/n*", indicating "number of something", are verbose.

   - "set", borrowed from cache concepts, is ambiguous and not meaning to a number.

   - "total/count/amount/capacity/volume" are verbose, and some of which are ambiguous.

3. Regarding n-th item:

   Often we need to refer to some specific item/entry/register/module among a set of them,

   - When we are building a memory device and referring a position in number form, we call
     it __"addr"__.

   - otherwise, __"index"__.

   Other English words and other abbreviations are discouraged, including:

   - "id/idx", we have sufficient storage for code.

   - "elem/element/item/entry", ambiguous. It can be referred as either the index (number)
     of item or the item itself.

4. Other rules: 

   - Do not use abbreviations unless the name is too long and verbose. If have to, use
     prefix-like abbreviations (abbrev.), not use acronym (idx, rst, clk). 
 		
   - Do not use uncommon abbreviations/acronym, unless it appears frequently in the code
     base. If have to use, make it well explained and documented.

 		common or conventional abbreviations:
 		- req/res - request/response
 		- prev/next/curr - previous/next/current
 		- R/W(suffix) - read/write
     - I/O(suffix) - input/output

     for example, the AXI4 Bus uses A/AW/R for Address, Address & Write, and Read. It is
     better to integrate them into your bus implementation.

   - If lot of names share same prefix, consider create a new namespace, which can be either
     package/class or other encapsuling method, and remove the prefix.

5. Advices:

   - When you feel not sure about name, please check out www.thesaurus.com. 


