<script setup lang="ts">
import {
  VButton,
  VCard,
  VPageHeader,
  VSpace,
  VTag,
  VEntity,
  VEntityField,
  VLoading,
  IconRefreshLine,
  VEmpty,
  VModal,
} from "@halo-dev/components";
import CarbonFolderDetailsReference from "~icons/carbon/folder-details-reference";
import {computed, onMounted, ref, watch} from "vue";
import {
  getApisApiPluginHaloRunV1Alpha1PluginsS3LinkPoliciesS3,
  getApisApiPluginHaloRunV1Alpha1PluginsS3LinkObjectsByPolicyName,
  postApisApiPluginHaloRunV1Alpha1PluginsS3LinkAttachmentsLink,
} from "@/controller";
import type {ObjectVo} from "@/interface";
import AttachmentFileTypeIcon from "@/components/AttachmentFileTypeIcon.vue";

const selectedFiles = ref<string[]>([]);
const policyName = ref<string>("");
const page = ref(1);
const size = ref(50);
const policyOptions = ref<{ label: string; value: string; attrs: any }[]>([{
  label: "请选择策略",
  value: "",
  attrs: {disabled: true}
}]);
const files = ref<ObjectVo[]>([]);
const isFetching = ref(false);
const checkedAll = ref(false);
const isShowModal = ref(false);
const currentToken = ref("");
const nextToken = ref("");
const hasMore = ref(false);
const linkTips = ref("");
const isLinking = ref(false);
const isFetchingPolicies = ref(true);

const emptyTips = computed(() => {
  if (isFetchingPolicies.value) {
    return "正在加载策略";
  } else {
    if (policyOptions.value.length <= 1) {
      return "没有可用的策略，请前往【附件】添加S3策略";
    } else {
      if (!policyName.value) {
        return "请在左上方选择策略";
      } else {
        return "该策略的 桶/文件夹 下没有文件";
      }
    }
  }
});

const handleCheckAllChange = (e: Event) => {
  const {checked} = e.target as HTMLInputElement;

  if (checked) {
    selectedFiles.value =
      files.value?.filter(file => !file.isLinked).map((file) => {
        return file.key || "";
      }) || [];
  } else {
    selectedFiles.value.length = 0;
  }
};


const fetchPolicies = async () => {
  try {
    const policiesData = await getApisApiPluginHaloRunV1Alpha1PluginsS3LinkPoliciesS3();
    if (policiesData.status == 200){
      policyOptions.value = [{
        label: "请选择策略",
        value: "",
        attrs: {disabled: true}
      }];
      policiesData.data.forEach((policy) => {
        policyOptions.value.push({
          label: policy.spec.displayName,
          value: policy.metadata.name,
          attrs: {}
        });
      });
    }
  } catch (error) {
    console.error(error);
  }
  isFetchingPolicies.value = false;
};


onMounted(() => {
  fetchPolicies();
});

watch(selectedFiles, (newValue) => {
  checkedAll.value = files.value?.filter(file => !file.isLinked)
      .filter(file => !newValue.includes(file.key || "")).length == 0
    && files.value?.length != 0;
});

const fetchObjects = async () => {
  if (!policyName.value) {
    return;
  }
  isFetching.value = true;
  files.value.length = 0;
  try {
    const objectsData = await getApisApiPluginHaloRunV1Alpha1PluginsS3LinkObjectsByPolicyName({
      policyName: policyName.value,
      pageSize: size.value,
      continuationToken: currentToken.value
    });
    if (objectsData.status == 200) {
      files.value = objectsData.data.objects;
      hasMore.value = objectsData.data.hasMore;
      currentToken.value = objectsData.data.currentToken;
      nextToken.value = objectsData.data.nextToken;

      if (files.value.length == 0 && hasMore.value && nextToken.value) {
        currentToken.value = nextToken.value;
        await fetchObjects();
      }
    }
  } catch (error) {
    console.error(error);
  }
  selectedFiles.value.length = 0;
  isFetching.value = false;
};

const checkSelection = (file: ObjectVo) => {
  return selectedFiles.value.includes(file.key || "");
};

const handleLink = async () => {
  isLinking.value = true;
  isShowModal.value = true;
  linkTips.value = `正在关联${selectedFiles.value.length}个文件`;
  const linkResult = await postApisApiPluginHaloRunV1Alpha1PluginsS3LinkAttachmentsLink({
    policyName: policyName.value,
    objectKeys: selectedFiles.value
  });

  const successCount = linkResult.data.items.filter(item => item.success).length;
  const failedCount = linkResult.data.items.filter(item => !item.success).length;
  linkTips.value = `关联成功${successCount}个文件，关联失败${failedCount}个文件`;

  if (failedCount > 0) {
    const failedItems = linkResult.data.items.filter(item => !item.success);
    const failedTips = failedItems.map(item => `${item.objectKey}:${item.message}`).join("\n");
    linkTips.value = `${linkTips.value}\n${failedTips}`;
  }
  isLinking.value = false;
};

const selectOneAndLink = (file: ObjectVo) => {
  selectedFiles.value = [file.key || ""];
  handleLink();
};

const handleNextPage = () => {
  if (!policyName.value) {
    return;
  }
  if (hasMore.value) {
    isFetching.value = true;
    page.value += 1;
    currentToken.value = nextToken.value;
    nextToken.value = "";
    fetchObjects();
  }
};

const handleFirstPage = () => {
  if (!policyName.value) {
    return;
  }
  isFetching.value = true;
  page.value = 1;
  currentToken.value = "";
  nextToken.value = "";
  fetchObjects();
};

const handleModalClose = () => {
  isShowModal.value = false;
  fetchObjects();
};

</script>

<template>
  <VPageHeader title="关联S3文件(Beta)">
    <template #icon>
      <CarbonFolderDetailsReference class="mr-2 self-center"/>
    </template>
  </VPageHeader>
  <div class="m-0 md:m-4">
    <VCard :body-class="['!p-0']">
      <template #header>
        <div class="block w-full bg-gray-50 px-4 py-3">
          <div
            class="relative flex flex-col items-start sm:flex-row sm:items-center"
          >
            <div
              v-permission="['system:users:aaa']"
              class="mr-4 hidden items-center sm:flex"
            >
              <input
                v-model="checkedAll"
                class="h-4 w-4 rounded border-gray-300 text-indigo-600"
                type="checkbox"
                @change="handleCheckAllChange"
              />
            </div>
            <div class="flex w-full flex-1 items-center sm:w-auto">
              <!-- 没选中就显示复选框 -->
              <div
                v-if="!selectedFiles.length"
                class="flex items-center gap-2"
              >
                策略:
                <FormKit
                  id="policyChoose"
                  outer-class="!p-0 w-48"
                  v-model="policyName"
                  name="policyName"
                  type="select"
                  :options="policyOptions"
                  @change="fetchObjects()"
                ></FormKit>

              </div>
              <!-- 选中就显示操作按钮 -->
              <VSpace v-else>
                <VButton type="primary" @click="handleLink">
                  关联
                </VButton>
              </VSpace>
            </div>
            <!-- 右边刷新按钮 -->
            <div class="mt-4 flex sm:mt-0">
              <!-- TODO: 仅查看未关联 -->
              <VSpace spacing="lg">
                <div class="flex flex-row gap-2">
                  <div
                    class="group cursor-pointer rounded p-1 hover:bg-gray-200"
                    @click="fetchObjects()"
                  >
                    <IconRefreshLine
                      v-tooltip="$t('core.common.buttons.refresh')"
                      :class="{
                        'animate-spin text-gray-900': isFetching,
                      }"
                      class="h-4 w-4 text-gray-600 group-hover:text-gray-900"
                    />
                  </div>
                </div>
              </VSpace>
            </div>
          </div>
        </div>
      </template>

      <VLoading v-if="isFetching"/>

      <Transition v-else-if="!files?.length" appear name="fade">
        <VEmpty
          message="空空如也"
          :title="emptyTips"
        >
        </VEmpty>
      </Transition>

      <Transition v-else appear name="fade">
        <ul
          class="box-border h-full w-full divide-y divide-gray-100"
          role="list"
        >
          <li v-for="(file, index) in files" :key="index">
            <VEntity :is-selected="checkSelection(file)">
              <template
                #checkbox
              >
                <input
                  v-model="selectedFiles"
                  :value="file.key || ''"
                  class="h-4 w-4 rounded border-gray-300 text-indigo-600"
                  name="post-checkbox"
                  :disabled="file.isLinked"
                  type="checkbox"
                />
              </template>
              <template #start>
                <VEntityField>
                  <template #description>
                    <AttachmentFileTypeIcon
                      :display-ext="false"
                      :file-name="file.displayName || ''"
                      :width="8"
                      :height="8"
                    />
                  </template>
                </VEntityField>
                <VEntityField
                  :title="file.displayName || ''"
                  :description="file.key || ''"
                />
              </template>
              <template #end>
                <VEntityField>
                  <template #description>
                    <VTag :theme="file.isLinked ? 'default':'primary'">
                      {{
                        file.isLinked ? '已关联' : '未关联'
                      }}
                    </VTag>
                  </template>
                </VEntityField>
                <VEntityField>
                  <template #description>
                    <VButton
                      :disabled="file.isLinked || false"
                      @click="selectOneAndLink(file)"
                    >
                      关联
                    </VButton>
                  </template>
                </VEntityField>
              </template>
            </VEntity>
          </li>
        </ul>
      </Transition>

      <template #footer>
        <div class="bg-white sm:flex sm:items-center sm:justify-end">
          <div class="inline-flex items-center gap-5">
            <span class="text-xs text-gray-500">已自动过滤文件夹对象，页面实际显示数量少为正常现象</span>
            <div class="inline-flex items-center gap-2">
              <VButton size="small" @click="handleFirstPage" :disabled="!policyName">返回第一页</VButton>

              <span class="text-sm text-gray-500">第 {{ page }} 页</span>

              <VButton size="small" @click="handleNextPage" :disabled="!hasMore || isFetching || !policyName">
                下一页
              </VButton>
            </div>
            <div class="inline-flex items-center gap-2">
              <select
                v-model="size"
                class="h-8 border outline-none rounded-base px-2 text-gray-800 text-sm border-gray-300"
                @change="handleFirstPage"
              >
                <option
                  v-for="(sizeOption, index) in [20, 50, 100, 200]"
                  :key="index"
                  :value="sizeOption"
                >
                  {{ sizeOption }}
                </option>
              </select>
              <span class="text-sm text-gray-500">条/页</span>
            </div>
          </div>
        </div>
      </template>
    </VCard>
  </div>
  <VModal
    :visible="isShowModal"
    :fullscreen="false"
    :title="'关联结果'"
    :width="500"
    :mount-to-body="true"
    @close="handleModalClose"
  >
    <template #footer>
      <VSpace>
        <VButton
          :loading="isLinking"
          type="primary"
          @click="handleModalClose"
        >
          确定
        </VButton>
      </VSpace>
    </template>
    <div class="flex flex-col">
      {{linkTips}}
    </div>
  </VModal>
</template>

<style lang="scss" scoped>

</style>
