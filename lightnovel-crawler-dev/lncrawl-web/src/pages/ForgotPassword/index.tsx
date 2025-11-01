import { stringifyError } from '@/utils/errors';
import {
  Alert,
  Avatar,
  Button,
  Card,
  Divider,
  Flex,
  Form,
  Input,
  Layout,
  Space,
  Typography,
} from 'antd';
import FormItem from 'antd/es/form/FormItem';
import axios from 'axios';
import { useState } from 'react';

export const ForgotPasswordPage: React.FC<any> = () => {
  const [form] = Form.useForm();
  const [error, setError] = useState<string>();
  const [success, setSuccess] = useState<boolean>(false);
  const [loading, setLoading] = useState<boolean>(false);

  const sendResetLink = async (data: any) => {
    if (success) return;
    setLoading(true);
    setSuccess(false);
    setError(undefined);
    try {
      await axios.post(`/api/auth/send-password-reset-link`, data);
      setSuccess(true);
    } catch (err) {
      setError(stringifyError(err, 'Oops! Something went wrong.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Layout
      style={{
        padding: '10px',
        overflow: 'auto',
        height: 'calc(100vh - 40px)',
      }}
    >
      <Layout.Content style={{ overflow: 'auto' }}>
        <Flex
          align="center"
          justify="center"
          style={{ width: '100%', height: '100%' }}
        >
          <Card
            title={
              <Space
                direction="vertical"
                align="center"
                style={{ padding: '15px', width: '100%' }}
              >
                <Avatar
                  src="/lncrawl.svg"
                  style={{ width: '96px', height: '96px' }}
                />
                <Typography.Title
                  type="success"
                  level={3}
                  style={{ margin: 0 }}
                >
                  Lightnovel Crawler
                </Typography.Title>
              </Space>
            }
            style={{ width: '400px' }}
          >
            <Form
              form={form}
              onFinish={sendResetLink}
              size="large"
              layout="vertical"
              labelCol={{ style: { padding: 0 } }}
            >
              <Form.Item
                name="email"
                label="Email"
                rules={[{ required: true }]}
              >
                <Input
                  placeholder="Enter email"
                  autoComplete="current-user"
                  disabled={success}
                />
              </Form.Item>

              {Boolean(error) ? (
                <Alert
                  type="warning"
                  showIcon
                  message={error}
                  closable
                  onClose={() => setError('')}
                />
              ) : success ? (
                <Alert
                  type="success"
                  showIcon
                  message={'Please check your email for a password reset link'}
                />
              ) : null}

              {!success ? (
                <FormItem style={{ marginTop: '20px' }}>
                  <Button
                    block
                    type="primary"
                    htmlType="submit"
                    loading={loading}
                    disabled={loading}
                    children={'Send Reset Link'}
                  />
                </FormItem>
              ) : (
                <Flex justify="center" style={{ marginTop: 20 }}>
                  <Typography.Link href="/forgot-password">
                    I have not receive any link
                  </Typography.Link>
                </Flex>
              )}
            </Form>

            <Divider />

            <Flex justify="center">
              <Typography.Link href="/signup">
                Create new account
              </Typography.Link>
            </Flex>
          </Card>
        </Flex>
      </Layout.Content>
    </Layout>
  );
};
