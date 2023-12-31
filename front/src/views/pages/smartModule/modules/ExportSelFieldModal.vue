<template>
  <BasicModal
    v-bind="$attrs"
    @register="registerModal"
    :title="getTitle"
    @ok="handleSubmit"
    :maskClosable="false"
  >
    <Transfer
      v-model:target-keys="targetKeys"
      v-model:selected-keys="selectedKeys"
      :data-source="mockData"
      show-search
      :titles="[' · 备选的域', ' · 已选的域']"
      :filter-option="filterOption"
      :render="(item) => item.title"
      :list-style="{
        width: '400px',
        height: '400px',
      }"
      @change="handleChange"
      @search="handleSearch"
      @select-change="handleSelectChange"
    >
      <template #children="{ direction, filteredItems, onItemSelect }">
        <div class="transfer">
          <div v-if="direction === 'right'" class="transfer-right">
            <div
              v-for="(item, index) in filteredItems"
              draggable="true"
              :key="item.key"
              @click="() => checkChange(item.checked, item.key, onItemSelect)"
              class="transfer-right-item cursor-move"
              @mouseenter="isTarget(true, item)"
              @mouseleave="isTarget(false, item)"
              @dragstart="handleDragstart(index)"
              @drop.prevent="handleDrop()"
              @dragover.prevent="handleDragover(index)"
            >
              <Checkbox :checkedKeys="[...targetKeys]" v-model:checked="item.checked" />
              <div class="transfer-right-item-content">
                <span> &nbsp;{{ item.title }}</span>
                <!-- <MenuOutlined v-show="item.showMenu" /> -->
                <div>
                  <Icon icon="ant-design:menu-outlined" />
                  <Icon
                    icon="ant-design:arrow-up-outlined"
                    class="cursor-pointer ml-2 mr-2"
                    @click.stop="handleSetIndex(index, 'up')"
                  />
                  <Icon
                    icon="ant-design:arrow-down-outlined"
                    class="cursor-pointer"
                    @click.stop="handleSetIndex(index, 'down')"
                  />
                </div>
              </div>
            </div>
          </div>
          <div v-if="direction === 'left'" class="transfer-left">
            <div
              v-for="item in filteredItems"
              :key="item.key"
              class="transfer-left-item cursor-pointer"
              @click="() => checkChange(item.checked, item.key, onItemSelect)"
            >
              <Checkbox :checkedKeys="[...targetKeys]" v-model:checked="item.checked" />
              &nbsp; {{ item.title }}
            </div>
          </div>
        </div>
      </template>
    </Transfer>
  </BasicModal>
</template>
<script lang="ts">
  import { defineComponent, ref } from 'vue';
  import { BasicModal, useModalInner } from '/@/components/Modal';
  import { Transfer, Checkbox } from 'ant-design-vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { downloadByData } from '/@/utils/file/download';
  import { getFieldsWithNest, getExportFields, getVisualExportExcel } from '/@/api/module/module';
  import Icon from '/@/components/Icon';
  interface MockData {
    key: string;
    title: string;
    description: string;
    chosen: boolean;
  }

  export default defineComponent({
    name: 'ExportSelFieldModal',
    components: { BasicModal, Transfer, Checkbox, Icon },
    emits: ['success', 'register'],
    setup(_, { emit }) {
      const isUpdate = ref(true);
      let formCode = ref('');
      let moduleCode = ref('');
      let moduleName = '';
      const { createMessage } = useMessage();

      let mockData = ref<MockData[]>([]);

      let targetKeys = ref<string[]>([]);
      let selectedKeys = ref<string[]>([]);
      let formParams = {};

      const [registerModal, { setModalProps, closeModal }] = useModalInner(async (data) => {
        setModalProps({ confirmLoading: false, width: '60%' });
        console.log('useModalInner data', data);
        isUpdate.value = !!data?.isUpdate;
        formCode.value = data.formCode;
        moduleCode.value = data.moduleCode;
        formParams = data.formParams;
        moduleName = data.moduleName;
        mockData.value = [];
        targetKeys.value = [];
        // 如果注释掉，会使默认已选的为空
        getExportFieldsList();
        getFieldsList();
      });

      const getTitle = '导出';

      async function handleSubmit() {
        try {
          setModalProps({ confirmLoading: true });
          // TODO custom api
          if (targetKeys.value.length == 0) {
            createMessage.warning('选择数据不能为空');
            return;
          }
          let params = {
            // moduleCode: moduleCode.value,
            exportColProps: targetKeys.value.join(','),
            ...formParams,
          };
          await getVisualExportExcel(params).then((res) => {
            downloadByData(res, moduleName + '.xls');
            closeModal();
            emit('success');
          });
        } finally {
          setModalProps({ confirmLoading: false });
        }
      }

      async function getExportFieldsList() {
        let params = {
          moduleCode: moduleCode.value,
        };
        await getExportFields(params).then((res) => {
          let data = res || [];
          data.forEach((item) => {
            item.key = item.name;
            item.title = item.title;
            item.description = item.title;
            targetKeys.value.push(item.name);
          });
        });
      }

      async function getFieldsList() {
        let params = {
          formCode: formCode.value,
        };
        await getFieldsWithNest(params).then((res) => {
          let data = res || [];
          data.forEach((item) => {
            item.key = item.name;
            item.title = item.title;
            item.description = item.title;
            item.checked = false;
          });
          data.unshift({ key: 'flowId', title: '流程号', description: '流程号', checked: false });
          data.unshift({ key: 'ID', title: 'ID', description: 'ID', checked: false });
          mockData.value = data;
        });
      }
      const filterOption = (inputValue: string, option: MockData) => {
        return option.description.indexOf(inputValue) > -1;
      };
      const handleChange = (keys: string[], direction: string, moveKeys: string[]) => {
        // mockData.value = mockData.value.map((item) => {
        //   return {
        //     ...item,
        //     checked: false,
        //   };
        // });

        if (direction === 'right') {
          // 将右移的元素置于末尾，元素较多时，置于末尾会看不到
          // targetKeys.value.push(...moveKeys);
          // targetKeys.value.splice(0, moveKeys.length);
        }
      };

      const handleSearch = (dir: string, value: string) => {};

      // 穿梭框列表图标显示与隐藏
      const isTarget = (flag, e) => {
        let key = false;
        targetKeys.value.forEach((item) => {
          if (e.key === item) key = item;
        });
        if (key !== false) {
          // drawerData.mockData.forEach((item, index) => {
          //   if (e.key === item.key) drawerData.mockData[index].showMenu = flag;
          // });
        }
      };

      let drawerData: any = {};
      const handleDrop = () => {
        // 删除老的
        const changeItem = targetKeys.value.splice(drawerData.oldItemIndex, 1)[0];
        // 在列表中目标位置增加新的
        targetKeys.value.splice(drawerData.newItemIndex, 0, changeItem);
      };

      const handleDragstart = (index) => {
        drawerData.oldItemIndex = index;
      };
      const handleDragover = (index) => {
        drawerData.newItemIndex = index;
      };

      //右侧排序
      const handleSetIndex = (index: Number, type: String) => {
        switch (type) {
          case 'up':
            if (index > 0) {
              handleDragstart(index);
              handleDragover(index - 1);
              handleDrop();
            }
            break;
          case 'down':
            if (index < mockData.value.length) {
              handleDragstart(index);
              handleDragover(index + 1);
              handleDrop();
            }
            break;
        }
      };

      // 用于判断选中了哪些多选框
      const checkChange = (checked, key, onItemSelect) => {
        mockData.value.forEach((items, index) => {
          if (key === items.key) mockData.value[index].chosen = !checked;
        });
        onItemSelect(key, !checked);
      };

      const handleSelectChange = (sourceSelectedKeys, targetSelectedKeys) => {
        mockData.value.forEach((item) => {
          if (sourceSelectedKeys.includes(item.key) || targetSelectedKeys.includes(item.key)) {
            item.checked = true;
          } else {
            item.checked = false;
          }
        });
      };

      return {
        registerModal,
        getTitle,
        handleSubmit,
        mockData,
        targetKeys,
        filterOption,
        handleChange,
        handleSearch,
        isTarget,
        handleDrop,
        handleDragstart,
        handleDragover,
        checkChange,
        selectedKeys,
        handleSetIndex,
        handleSelectChange,
      };
    },
  });
</script>
<style lang="less" scoped>
  :deep(.ant-transfer-list-body) {
    width: 100%;
    height: 400px;
  }
  :deep(.ant-transfer-list-body-customize-wrapper) {
    padding: 0 12px 0 0px;
    height: 100%;
  }
  .transfer {
    height: 400px;
    overflow: hidden;
  }
  .transfer-left,
  .transfer-right {
    width: 100%;
    height: 400px;
    overflow-y: auto;
    &-item {
      padding-left: 12px;
      width: 100%;
      height: 30px;
      display: flex;
      align-items: center;
      &-content {
        width: 100%;
        display: flex;
        padding: 10px;
        align-items: center;
        justify-content: space-between;
      }
    }
    &-item:hover {
      background: #ccc;
    }
  }
</style>
