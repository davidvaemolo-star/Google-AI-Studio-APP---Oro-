/*
 * audio_prompts.h – DEPRECATED
 *
 * Voice prompt audio data has been moved to the XIAO nRF52840's onboard
 * 2 MB QSPI flash chip.  See audio_qspi.h / audio_qspi.cpp.
 *
 * To provision the QSPI flash:
 *   1. Run  generate_qspi_binary.py          → produces audio_qspi.bin
 *   2. Flash audio_qspi.bin to the device's QSPI flash starting at address 0
 *      using nrfjprog:
 *        nrfjprog --qspieraseall
 *        nrfjprog --qspiwrite audio_qspi.bin --qspiaddr 0
 *      Or use the companion Arduino sketch in tools/QSPIProvisioner/
 *
 * This file is kept as a placeholder so existing build-system references
 * do not break.  It defines no symbols.
 */

#ifndef AUDIO_PROMPTS_H
#define AUDIO_PROMPTS_H
// No content – all audio is on QSPI flash.
#endif // AUDIO_PROMPTS_H
