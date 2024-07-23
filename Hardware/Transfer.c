#include "stm32f10x.h"  
#include "Serial.h"
#include "Delay.h"
#include "string.h"

void encoded_on(void)
{
	GPIO_SetBits(GPIOA, GPIO_Pin_0);
	Serial_SendByte('1');
	Delay_us(250);
	GPIO_ResetBits(GPIOA, GPIO_Pin_0);
	Serial_SendByte('0');
	Delay_us(250);
}

void encoded_off(void)
{
	GPIO_ResetBits(GPIOA, GPIO_Pin_0);
	Serial_SendByte('0');
	Delay_us(250);
	GPIO_SetBits(GPIOA, GPIO_Pin_0);
	Serial_SendByte('1');
	Delay_us(250);
}

void CharToGPIO(char x)
{
	char byte = x;
	int bitIndex = 7;
	while(bitIndex >= 0)
	{
		if (byte & (1 << bitIndex))
		{
			encoded_on();
		} 
		else 
		{
			encoded_off();
		}
		bitIndex--;
	}
}

void Transfer(uint8_t i,char *url)
{
	int x = 0;
	for(; x < i; x++)
	{
		char byte = url[x];
		CharToGPIO(byte);
	}
}

void Transfer_Preamble(void)
{
	for(int i = 0; i < 3; i++)
	{
		encoded_on();
		encoded_off();
		encoded_on();
		encoded_off();
		encoded_on();
		encoded_off();
		encoded_on();
		encoded_off();
	}
}

void Transfer_Tailer(void)
{
	encoded_on();
	encoded_off();
	encoded_on();
	encoded_off();
	encoded_on();
	encoded_on();
	encoded_on();
	encoded_on();
}

void Transfer_Head(void)
{
	encoded_on();
	encoded_off();
	encoded_on();
	encoded_off();
	encoded_on();
	encoded_off();
	encoded_on();
	encoded_on();
}

void GPIOA0_Init_As_GPIO(void)
{
    GPIO_InitTypeDef GPIO_InitStructure;
	
    // 配置PA0为普通推挽输出
    GPIO_InitStructure.GPIO_Pin = GPIO_Pin_0;
    GPIO_InitStructure.GPIO_Mode = GPIO_Mode_Out_PP;
    GPIO_InitStructure.GPIO_Speed = GPIO_Speed_50MHz;
    GPIO_Init(GPIOA, &GPIO_InitStructure);
}
