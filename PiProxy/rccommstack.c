#include "rccommstack.h"
#include "stdlib.h"

RcCommStackInstance* create_rccommstack_instance(RcCommStackListener)
{
	struct RcCommStackInstance* instance = (struct RcCommStackInstance*) malloc(sizeof(RcCommStackInstance));
}


void appendNewData(RcCommStackInstance* stack_instance, char* new_data)
{

}