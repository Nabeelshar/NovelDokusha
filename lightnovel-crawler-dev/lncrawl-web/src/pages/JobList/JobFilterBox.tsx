import { stringifyError } from '@/utils/errors';
import { PlayCircleFilled, XFilled } from '@ant-design/icons';
import { Button, Flex, message, Select, Typography } from 'antd';
import axios from 'axios';
import { useEffect, useState } from 'react';
import { JobStatusFilterParams } from './constants';
import type { JobListHook } from './hooks';
import { useSelector } from 'react-redux';
import { Auth } from '@/store/_auth';

export const JobFilterBox: React.FC<
  Pick<JobListHook, 'status' | 'updateParams'>
> = ({ status, updateParams }) => {
  const isAdmin = useSelector(Auth.select.isAdmin);
  const [isRunning, setIsRunning] = useState<boolean>();
  const [messageApi, contextHolder] = message.useMessage();

  const fetchStatus = async () => {
    try {
      const resp = await axios.get<boolean>(`/api/runner/status`);
      console.log('resp', resp.data);
      return Boolean(resp.data);
    } catch {
      return undefined;
    }
  };

  const startRunner = async () => {
    try {
      await axios.post(`/api/runner/start`);
      setIsRunning(true);
    } catch (err) {
      messageApi.open({
        type: 'error',
        content: stringifyError(err, 'Something went wrong!'),
      });
    }
  };

  const stopRunner = async () => {
    try {
      await axios.post(`/api/runner/stop`);
      setIsRunning(false);
    } catch (err) {
      messageApi.open({
        type: 'error',
        content: stringifyError(err, 'Something went wrong!'),
      });
    }
  };

  useEffect(() => {
    if (!isAdmin) return;
    fetchStatus().then(setIsRunning);
    const iid = setInterval(() => {
      fetchStatus().then(setIsRunning);
    }, 5000);
    return () => clearInterval(iid);
  }, [isAdmin]);

  return (
    <Flex align="center" gap={5}>
      {contextHolder}

      <Typography.Text>Status:</Typography.Text>
      <Select
        allowClear
        defaultValue={status || JobStatusFilterParams[0].value}
        onChange={(status) => updateParams({ status, page: 1 })}
        placeholder="Filter by Status"
        style={{ minWidth: 150 }}
        options={JobStatusFilterParams}
      />

      <div style={{ flex: 1 }} />

      {isAdmin && (
        <>
          {typeof isRunning === 'undefined' ? null : isRunning ? (
            <Button onClick={stopRunner} icon={<XFilled />} danger>
              Stop Jobs
            </Button>
          ) : (
            <Button
              onClick={startRunner}
              icon={<PlayCircleFilled />}
              type="primary"
            >
              Start Jobs
            </Button>
          )}
        </>
      )}
    </Flex>
  );
};
