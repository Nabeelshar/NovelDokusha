import { stringifyError } from '@/utils/errors';
import { EditOutlined, LockOutlined, SaveOutlined } from '@ant-design/icons';
import {
  Button,
  Divider,
  Flex,
  Form,
  Input,
  Modal,
  Space,
  message,
} from 'antd';
import axios from 'axios';
import { useState } from 'react';

export const ProfilePasswordChangeButton: React.FC<any> = () => {
  const [messageApi, contextHolder] = message.useMessage();

  const [open, setOpen] = useState(false);
  const [changing, setChanging] = useState(false);

  const handleChangePassword = async (values: {
    current_password: string;
    new_password: string;
  }) => {
    if (values.current_password === values.new_password) {
      messageApi.info('New and old passwords are the same');
      return;
    }
    try {
      setChanging(true);
      await axios.put('/api/auth/me/password', {
        old_password: values.current_password,
        new_password: values.new_password,
      });
      messageApi.success('Password changed successfully');
      setOpen(false);
    } catch (err) {
      console.error(err);
      messageApi.error(stringifyError(err));
    } finally {
      setChanging(false);
    }
  };

  return (
    <>
      {contextHolder}

      <Button
        type="primary"
        icon={<EditOutlined />}
        onClick={() => setOpen(true)}
      >
        Change Password
      </Button>

      <Modal
        title={
          <Space>
            <LockOutlined /> Change Password
          </Space>
        }
        open={open}
        onCancel={() => setOpen(false)}
        footer={null}
        destroyOnHidden
      >
        <Form size="large" layout="vertical" onFinish={handleChangePassword}>
          <Form.Item
            name="current_password"
            label="Current Password"
            rules={[
              { required: true, message: 'Please enter your current password' },
            ]}
          >
            <Input.Password
              placeholder="Current password"
              autoComplete="current-password"
            />
          </Form.Item>

          <Form.Item
            name="new_password"
            label="New Password"
            rules={[
              { required: true, message: 'Please enter a new password' },
              { min: 6, message: 'Password must be at least 6 characters' },
            ]}
            hasFeedback
          >
            <Input.Password
              placeholder="New password"
              autoComplete="new-password"
            />
          </Form.Item>

          <Divider size="small" />

          <Flex gap={10} justify="end">
            <Button onClick={() => setOpen(false)}>Cancel</Button>
            <Button
              type="primary"
              htmlType="submit"
              loading={changing}
              icon={<SaveOutlined />}
            >
              Update Password
            </Button>
          </Flex>
        </Form>
      </Modal>
    </>
  );
};
