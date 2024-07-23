#include "stm32f10x.h"                  // Device header
#include "stdbool.h"
#include "Delay.h"
#include "OLED.h"
#include "Serial.h"
#include "LED.h"
#include "string.h"
#include "Transfer.h"
#include "PWM.h"

int main(void)
{
	OLED_Init();
	PWM_Init();
	Serial_Init();
	OLED_ShowString(1, 1, "RxPacket");
	int pre = 32;
	int head = 8;
	int tail = 7;
	char preamble[100] = {1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0};
	char tailer[100] = {1,0,1,0,1,1,1,1};
	while (1)
	{
		bool PWM_ON = true;
		if (Serial_RxFlag == 1)
		{
			TIM_Cmd(TIM2, DISABLE);
			GPIOA0_Init_As_GPIO();
			OLED_ShowString(2, 1, Serial_RxPacket);
			Transfer_Preamble();
			Transfer_Head();
			int i = 0;
			while(Serial_RxPacket[i] != '\0') i++;
			Transfer(i, Serial_RxPacket);
			Transfer_Tailer();
			Serial_RxFlag = 0;
			PWM_ON = false;
		} else if (Serial_RxPacket[0] != '~'){
			int i = 0;
			Transfer_Preamble();
			Transfer_Head();
			while(Serial_RxPacket[i] != '\0') i++;
			Transfer(i, Serial_RxPacket);
			Transfer_Tailer();
			Serial_RxFlag = 0;
			PWM_ON = false;
		} else {
//			if(PWM_ON) break;
			PWM_Init();
		}
	}
}
