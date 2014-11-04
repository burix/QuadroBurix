#ifndef RCCOMMSTACK_H
#define RCCOMMSTACK_H

#define RcCommStackListener void (*stack_command_callback)(int throttleValue, int yawValue, int pitchValue, int rollValue)

struct RcCommStackInstance
{
    RcCommStackListener;
    char* buffer = 0x0;
    int write_pos = 0;
}

RcCommStackInstance* create_rccommstack_instance(RcCommStackListener);
void appendNewData(RcCommStackInstance* stack_instance, char)

#endif