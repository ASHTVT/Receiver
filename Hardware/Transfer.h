#ifndef __TRANSFER_H
#define __TRANSFER_H

void CharToGPIO(char x);
void Transfer(uint8_t i,char *url);
void GPIOA0_Init_As_GPIO(void);
void encoded_on(void);
void encoded_off(void);
void Transfer_Head(void);
void Transfer_Tailer(void);
void Transfer_Preamble(void);

#endif
