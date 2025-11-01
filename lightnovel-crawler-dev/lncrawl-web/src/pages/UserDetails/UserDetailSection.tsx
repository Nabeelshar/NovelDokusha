import { UserAvatar } from '@/components/Tags/gravatar';
import {
  UserRoleTag,
  UserStatusTag,
  UserTierTag,
} from '@/components/Tags/users';
import type { User } from '@/types';
import { stringifyError } from '@/utils/errors';
import { formatDate, formatFromNow } from '@/utils/time';
import {
  CalendarOutlined,
  CrownOutlined,
  DeleteOutlined,
  IdcardOutlined,
  MailOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from '@ant-design/icons';
import {
  Button,
  Descriptions,
  Divider,
  Flex,
  Grid,
  message,
  Popconfirm,
  Result,
  Space,
  Spin,
  Typography,
} from 'antd';
import axios from 'axios';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { UserActionButtons } from '../UserList/UserActionButtons';
import { UserEditButton } from './UserEditButton';

export const UserDetailSection: React.FC<{ userId: string }> = ({ userId }) => {
  const navigate = useNavigate();
  const { xs } = Grid.useBreakpoint();
  const [messageApi, contextHolder] = message.useMessage();

  const [user, setUser] = useState<User>();
  const [refreshId, setRefreshId] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    const fetchUser = async (id: string) => {
      setError(undefined);
      try {
        const { data } = await axios.get<User>(`/api/user/${id}`);
        setUser(data);
      } catch (err: any) {
        setError(stringifyError(err));
      } finally {
        setLoading(false);
      }
    };
    fetchUser(userId);
  }, [userId, refreshId]);

  const handleDelete = async () => {
    setDeleting(true);
    try {
      await axios.delete(`/api/user/${userId}`);
      messageApi.success('Permanently deleted the user');
      navigate('/admin/users');
    } catch (err) {
      console.error(err);
      messageApi.error(stringifyError(err));
    } finally {
      setDeleting(false);
    }
  };

  if (loading) {
    return (
      <Flex align="center" justify="center" style={{ height: '100%' }}>
        <Spin size="large" style={{ marginTop: 100 }} />
      </Flex>
    );
  }

  if (error || !user) {
    return (
      <Flex align="center" justify="center" style={{ height: '100%' }}>
        <Result
          status="error"
          title="Failed to load user details"
          subTitle={error}
          extra={[
            <Button onClick={() => setRefreshId((v) => v + 1)}>Retry</Button>,
          ]}
        />
      </Flex>
    );
  }

  return (
    <section style={{ maxWidth: 800, margin: '0 auto' }}>
      {contextHolder}

      <Typography.Title level={2}>
        <UserOutlined /> Profile
      </Typography.Title>

      <Descriptions
        bordered
        column={1}
        size="middle"
        layout={xs ? 'vertical' : 'horizontal'}
        styles={{ label: { width: 150, fontWeight: 500 } }}
      >
        <Descriptions.Item
          label={
            <Space>
              <IdcardOutlined /> User ID
            </Space>
          }
        >
          <Typography.Text copyable>{user.id}</Typography.Text>
        </Descriptions.Item>

        <Descriptions.Item
          label={
            <Space>
              <UserOutlined /> Name
            </Space>
          }
        >
          <Space>
            <UserAvatar user={user} size={32} />
            <Typography.Text>{user.name}</Typography.Text>
          </Space>
        </Descriptions.Item>

        <Descriptions.Item
          label={
            <Space>
              <MailOutlined /> Email
            </Space>
          }
        >
          <Typography.Text copyable>{user.email}</Typography.Text>
        </Descriptions.Item>

        <Descriptions.Item
          label={
            <Space>
              <SafetyCertificateOutlined /> Role
            </Space>
          }
        >
          <UserRoleTag value={user.role} />
        </Descriptions.Item>

        <Descriptions.Item
          label={
            <Space>
              <CrownOutlined /> Tier
            </Space>
          }
        >
          <UserTierTag value={user.tier} />
        </Descriptions.Item>

        <Descriptions.Item
          label={
            <Space>
              <UserOutlined /> Status
            </Space>
          }
        >
          <Space>
            <UserStatusTag value={user.is_active} />
            <Divider type="vertical" />
            <UserActionButtons
              user={user}
              onChange={() => setRefreshId((v) => v + 1)}
            />
          </Space>
        </Descriptions.Item>

        <Descriptions.Item
          label={
            <Space>
              <CalendarOutlined /> Joined
            </Space>
          }
        >
          <Typography.Text>{formatDate(user.created_at)}</Typography.Text>
          <Divider type="vertical" />
          <Typography.Text type="secondary">
            {formatFromNow(user.created_at)}
          </Typography.Text>
        </Descriptions.Item>

        <Descriptions.Item
          label={
            <Space>
              <CalendarOutlined /> Last Update
            </Space>
          }
        >
          <Typography.Text>{formatDate(user.updated_at)}</Typography.Text>
          <Divider type="vertical" />
          <Typography.Text type="secondary">
            {formatFromNow(user.updated_at)}
          </Typography.Text>
        </Descriptions.Item>
      </Descriptions>

      <Flex wrap gap={8} align="center" justify="end" style={{ marginTop: 10 }}>
        <UserEditButton
          user={user}
          block={xs}
          size="large"
          shape="round"
          onChange={() => setRefreshId((v) => v + 1)}
        />
        <Popconfirm
          title="Are you sure?"
          description="This will permanently delete the user."
          okText="Yes, delete"
          okType="danger"
          cancelText="Cancel"
          onConfirm={handleDelete}
        >
          <Button
            block={xs}
            size="large"
            shape="round"
            icon={<DeleteOutlined />}
            loading={deleting}
          >
            Delete Permanently
          </Button>
        </Popconfirm>
      </Flex>
    </section>
  );
};
