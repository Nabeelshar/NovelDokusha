import { JobStatus, RunState } from '@/types';

export const JobStatusFilterParams = [
  {
    value: 'any',
    label: 'Any',
  },
  {
    value: 'completed',
    label: 'Completed',
    params: { status: JobStatus.COMPLETED },
  },
  {
    value: 'successful',
    label: 'Successful',
    params: { run_state: RunState.SUCCESS },
  },
  {
    value: 'failed',
    label: 'Failed',
    params: { run_state: RunState.FAILED },
  },
  {
    value: 'canceled',
    label: 'Canceled',
    params: { run_state: RunState.CANCELED },
  },
  {
    value: 'pending',
    label: 'Pending',
    params: { status: JobStatus.PENDING },
  },
  {
    value: 'running',
    label: 'Running',
    params: { status: JobStatus.RUNNING },
  },
];
