import { UserRoleTag, UserTierTag } from '@/components/Tags/users';
import { UserRole, UserTier, type User } from '@/types';
import { stringifyError } from '@/utils/errors';
import { EditOutlined, SaveOutlined } from '@ant-design/icons';
import {
  Button,
  Divider,
  Flex,
  Form,
  Input,
  Modal,
  Select,
  Space,
  message,
  type ButtonProps,
} from 'antd';
import axios from 'axios';
import { useState } from 'react';

export const UserEditButton: React.FC<
  {
    user: User;
    onChange?: () => any;
  } & ButtonProps
> = ({ user, onChange, ...buttonProps }) => {
  const [messageApi, contextHolder] = message.useMessage();

  const [editOpen, setEditOpen] = useState(false);
  const [updating, setUpdating] = useState(false);

  const handleUpdateName = async (values: {
    name?: string;
    password?: string;
    role?: string;
    tier?: number;
  }) => {
    try {
      setUpdating(true);
      const changes: any = {};
      if (values.password?.trim()) {
        changes.password = values.password.trim();
      }
      if (values.role && values.role !== user.role) {
        changes.role = values.role;
      }
      if (values.tier && values.tier !== user.tier) {
        changes.tier = values.tier;
      }
      if (!Object.keys(changes).length) {
        messageApi.info('No value was updated');
        return;
      }
      await axios.put(`/api/user/${user.id}`, changes);
      messageApi.success('User updated successfully');
      setEditOpen(false);
      onChange && onChange();
    } catch (err) {
      console.error(err);
      messageApi.error(stringifyError(err));
    } finally {
      setUpdating(false);
    }
  };

  return (
    <>
      {contextHolder}

      <Button
        icon={<EditOutlined />}
        {...buttonProps}
        onClick={() => setEditOpen(true)}
      >
        Edit User
      </Button>

      <Modal
        title={
          <Space>
            <EditOutlined /> Edit Name
          </Space>
        }
        open={editOpen}
        footer={null}
        onCancel={() => setEditOpen(false)}
        destroyOnHidden
      >
        <Form
          size="large"
          layout="vertical"
          initialValues={user}
          onFinish={handleUpdateName}
        >
          <Form.Item
            name="name"
            label="Full Name"
            rules={[{ min: 2, message: 'Name must be at least 2 characters' }]}
          >
            <Input placeholder="Enter full name" autoComplete="name" />
          </Form.Item>

          <Form.Item
            name="email"
            label="Email"
            rules={[{ type: 'email', message: 'Please enter a valid email' }]}
          >
            <Input placeholder="Enter email" autoComplete="new-user" />
          </Form.Item>

          <Form.Item name="role" label="Role">
            <Select
              placeholder="Select role"
              options={Object.values(UserRole).map((value) => ({
                value,
                label: <UserRoleTag value={value} />,
              }))}
            />
          </Form.Item>

          <Form.Item name="tier" label="Tier">
            <Select
              placeholder="Select tier"
              options={Object.values(UserTier).map((value) => ({
                value,
                label: <UserTierTag value={value} />,
              }))}
            />
          </Form.Item>

          <Form.Item
            name={'password'}
            label="Password"
            rules={[
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
            <Button onClick={() => setEditOpen(false)}>Cancel</Button>
            <Button
              type="primary"
              htmlType="submit"
              loading={updating}
              icon={<SaveOutlined />}
            >
              Save
            </Button>
          </Flex>
        </Form>
      </Modal>
    </>
  );
};
